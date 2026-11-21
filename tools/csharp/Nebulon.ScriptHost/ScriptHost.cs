using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.Emit;

namespace Nebulon.ScriptHost
{
    public static class ScriptHost
    {
        [MethodImpl(MethodImplOptions.InternalCall)]
        private static extern void NativePing(string message);

        private static string referenceCacheDir;
        private static List<MetadataReference> referenceCache;

        public static string Run(string argument)
        {
            NativePing(Environment.Version + "|" + argument);
            return "C# " + Environment.Version + " ejecutandose dentro de Nebulon Engine (" + argument + ")";
        }

        public static string Compile(string outputPath, string filesList, string referenceDir)
        {
            Engine.Log("compile: enter");
            if (string.IsNullOrEmpty(outputPath)) return "ERR:falta la ruta de salida";
            if (string.IsNullOrEmpty(referenceDir)) return "ERR:falta la carpeta de referencias";

            string[] files = (filesList ?? "")
                    .Split(new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries)
                    .Select(f => f.Trim())
                    .Where(f => f.Length > 0)
                    .ToArray();
            if (files.Length == 0) return "ERR:no hay archivos C# para compilar";
            foreach (string file in files)
            {
                if (!File.Exists(file)) return "ERR:no existe " + file;
            }

            try
            {
                Engine.Log("compile: parse");
                CSharpParseOptions parse = new CSharpParseOptions(
                        LanguageVersion.CSharp12, DocumentationMode.None);
                SyntaxTree[] trees = files
                        .Select(file => CSharpSyntaxTree.ParseText(
                                File.ReadAllText(file), parse, file))
                        .ToArray();

                Engine.Log("compile: references");
                List<MetadataReference> references = BuildReferences(referenceDir);
                Engine.Log("compile: references=" + references.Count);
                if (references.Count == 0) return "ERR:no se encontraron ensamblados de referencia";

                Engine.Log("compile: create");
                CSharpCompilation compilation = CSharpCompilation.Create(
                        "NebulonUserScripts_" + Guid.NewGuid().ToString("N"),
                        trees,
                        references,
                        new CSharpCompilationOptions(
                                OutputKind.DynamicallyLinkedLibrary,
                                optimizationLevel: OptimizationLevel.Release,
                                allowUnsafe: true,
                                deterministic: true,
                                concurrentBuild: false,
                                nullableContextOptions: NullableContextOptions.Disable));

                using (MemoryStream stream = new MemoryStream())
                {
                    Engine.Log("compile: emit");
                    EmitResult result = compilation.Emit(stream);
                    Engine.Log("compile: emitted success=" + result.Success);
                    string diagnostics = FormatDiagnostics(result.Diagnostics, files);
                    if (!result.Success)
                    {
                        return "ERR:" + (diagnostics.Length == 0 ? "compilacion fallida" : diagnostics);
                    }

                    string parent = Path.GetDirectoryName(outputPath);
                    if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                    File.WriteAllBytes(outputPath, stream.ToArray());
                    return "OK:" + outputPath
                            + (diagnostics.Length == 0 ? "" : "\n" + diagnostics);
                }
            }
            catch (Exception error)
            {
                return "ERR:" + error.Message;
            }
        }

        public static string Execute(string assemblyPath, string typeName, string methodName, string argument)
        {
            if (string.IsNullOrEmpty(assemblyPath) || !File.Exists(assemblyPath))
                return "ERR:no existe el ensamblado compilado";

            TextWriter oldOut = Console.Out;
            TextWriter oldError = Console.Error;
            StringWriter output = new StringWriter();
            try
            {
                Console.SetOut(output);
                Console.SetError(output);
                Assembly assembly = Assembly.Load(File.ReadAllBytes(assemblyPath));
                return InvokeEntry(assembly, typeName, methodName, argument, output);
            }
            catch (Exception error)
            {
                Exception inner = error is TargetInvocationException && error.InnerException != null
                        ? error.InnerException
                        : error;
                return "ERR:" + inner.GetType().Name + ": " + inner.Message;
            }
            finally
            {
                Console.SetOut(oldOut);
                Console.SetError(oldError);
            }
        }

        private static string InvokeEntry(Assembly assembly, string typeName, string methodName,
                                          string argument, StringWriter output)
        {
            Type type = ResolveType(assembly, typeName);
            if (type == null) return "ERR:no se encontro la clase " + typeName;

            MethodInfo method = ResolveMethod(type, methodName);
            if (method != null)
            {
                object instance = method.IsStatic ? null : Activator.CreateInstance(type);
                object[] args = BuildArguments(method, argument);
                object result = method.Invoke(instance, args);
                return Finish("OK", result, output);
            }

            if (type.IsAbstract) return "ERR:" + type.FullName + " no tiene un metodo ejecutable";

            object script = Activator.CreateInstance(type);
            MethodInfo start = type.GetMethod("Start", BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance);
            MethodInfo update = type.GetMethod("Update", BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance);
            if (start == null && update == null)
                return "ERR:escribe un metodo Main, Run, Start o Update";

            if (start != null) start.Invoke(script, BuildArguments(start, argument));
            if (update != null) update.Invoke(script, BuildArguments(update, "0.016"));
            return Finish("OK", null, output);
        }

        private static Type ResolveType(Assembly assembly, string typeName)
        {
            if (!string.IsNullOrEmpty(typeName))
            {
                Type exact = assembly.GetType(typeName);
                if (exact != null) return exact;
                return assembly.GetTypes().FirstOrDefault(t => t.Name == typeName);
            }

            Type[] types = assembly.GetTypes();
            return types.FirstOrDefault(t => HasEntry(t, "Main"))
                    ?? types.FirstOrDefault(t => HasEntry(t, "Run"))
                    ?? types.FirstOrDefault(t => HasEntry(t, "Start") || HasEntry(t, "Update"))
                    ?? types.FirstOrDefault();
        }

        private static MethodInfo ResolveMethod(Type type, string methodName)
        {
            if (!string.IsNullOrEmpty(methodName))
            {
                MethodInfo named = type.GetMethod(methodName,
                        BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static | BindingFlags.Instance);
                if (named != null) return named;
            }

            MethodInfo main = type.GetMethod("Main",
                    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
            if (main != null) return main;
            return type.GetMethod("Run",
                    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
        }

        private static bool HasEntry(Type type, string name)
        {
            MethodInfo method = type.GetMethod(name,
                    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
            return method != null;
        }

        private static object[] BuildArguments(MethodInfo method, string argument)
        {
            ParameterInfo[] parameters = method.GetParameters();
            if (parameters.Length == 0) return new object[0];
            if (parameters.Length == 1 && parameters[0].ParameterType == typeof(string))
                return new object[] { argument ?? "" };
            if (parameters.Length == 1 && parameters[0].ParameterType == typeof(string[]))
                return new object[] { new[] { argument ?? "" } };
            if (parameters.Length == 1 && parameters[0].ParameterType == typeof(float))
                return new object[] { float.TryParse(argument, out float value) ? value : 0.016f };
            if (parameters.Length == 1 && parameters[0].ParameterType == typeof(double))
                return new object[] { double.TryParse(argument, out double value) ? value : 0.016 };
            return new object[parameters.Length];
        }

        private static string Finish(string status, object result, StringWriter output)
        {
            string text = output.ToString();
            StringBuilder builder = new StringBuilder(status);
            if (!string.IsNullOrEmpty(text)) builder.Append('\n').Append(text.TrimEnd());
            if (result != null) builder.Append("\n=> ").Append(result);
            return builder.ToString();
        }

        private static List<MetadataReference> BuildReferences(string referenceDir)
        {
            if (referenceCache != null && referenceCacheDir == referenceDir)
                return referenceCache;

            List<MetadataReference> references = new List<MetadataReference>();
            HashSet<string> seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            foreach (string path in Directory.GetFiles(referenceDir, "*.dll", SearchOption.TopDirectoryOnly))
            {
                string name = Path.GetFileName(path);
                if (name.StartsWith("Microsoft.CodeAnalysis", StringComparison.OrdinalIgnoreCase)) continue;
                if (name.StartsWith("Nebulon.ScriptHost", StringComparison.OrdinalIgnoreCase)) continue;
                if (!seen.Add(name)) continue;
                try
                {
                    references.Add(MetadataReference.CreateFromFile(path));
                }
                catch
                {
                }
            }

            string host = typeof(ScriptHost).Assembly.Location;
            if (!string.IsNullOrEmpty(host) && File.Exists(host) && seen.Add(Path.GetFileName(host)))
                references.Add(MetadataReference.CreateFromFile(host));

            referenceCacheDir = referenceDir;
            referenceCache = references;
            return references;
        }

        private static string FormatDiagnostics(IEnumerable<Diagnostic> diagnostics, string[] files)
        {
            StringBuilder builder = new StringBuilder();
            foreach (Diagnostic diagnostic in diagnostics)
            {
                if (diagnostic.Severity != DiagnosticSeverity.Error) continue;
                if (builder.Length > 0) builder.Append('\n');
                FileLinePositionSpan span = diagnostic.Location.GetLineSpan();
                string file = span.Path;
                if (string.IsNullOrEmpty(file)) file = files.Length > 0 ? files[0] : "script.cs";
                builder.Append(Path.GetFileName(file))
                        .Append(':')
                        .Append(span.StartLinePosition.Line + 1)
                        .Append(':')
                        .Append(span.StartLinePosition.Character + 1)
                        .Append(": ")
                        .Append(diagnostic.Id)
                        .Append(' ')
                        .Append(diagnostic.GetMessage());
            }
            return builder.ToString();
        }
    }
}
