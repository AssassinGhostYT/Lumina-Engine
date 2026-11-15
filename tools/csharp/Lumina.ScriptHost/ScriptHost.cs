using System;
using System.Runtime.CompilerServices;

namespace Lumina.ScriptHost
{
    /// <summary>
    /// Entry point used by the Lumina native bridge while the C# scripting
    /// runtime is being brought up. The real behaviour API will live here.
    /// </summary>
    public static class ScriptHost
    {
        [MethodImpl(MethodImplOptions.InternalCall)]
        private static extern void NativePing(string message);

        public static string Run(string argument)
        {
            string version = Environment.Version.ToString();
            NativePing(version + "|" + argument);
            return "C# " + version + " ejecutandose dentro de Lumina (" + argument + ")";
        }
    }
}
