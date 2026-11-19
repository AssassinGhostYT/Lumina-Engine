using System;
using System.Globalization;
using System.Runtime.CompilerServices;

namespace Nebulon
{
    /// <summary>Entry point for C# scripts running inside Nebulon Engine.</summary>
    public static class Engine
    {
        [MethodImpl(MethodImplOptions.InternalCall)]
        private static extern string NativeCall(string command, string a, string b, float value);

        [MethodImpl(MethodImplOptions.InternalCall)]
        private static extern void NativeLog(string level, string message);

        public static string Version => "1.0.6";

        /// <summary>True while a game scene is running (not just the editor).</summary>
        public static bool IsGameRunning => Call("running") == "1";

        /// <summary>Name of the scene that is currently running.</summary>
        public static string SceneName => CallOrThrow("scene");

        /// <summary>Seconds since the previous frame of the running scene.</summary>
        public static float DeltaTime => ToFloat(CallOrThrow("delta"), 0f);

        /// <summary>Input of the running game (touch and keyboard).</summary>
        public static Input Input { get; } = new Input();

        /// <summary>Finds an element by name in the active scene, or null when it does not exist.</summary>
        public static Entity Find(string name)
        {
            if (string.IsNullOrEmpty(name)) return null;
            return CallOrThrow("find", name) == "1" ? new Entity(name) : null;
        }

        /// <summary>Opens another scene of the project by name.</summary>
        public static bool ChangeScene(string scene)
        {
            if (string.IsNullOrEmpty(scene)) return false;
            return CallOrThrow("changeScene", scene) == "OK";
        }

        /// <summary>Destroys an element by name.</summary>
        public static bool Destroy(string name)
        {
            if (string.IsNullOrEmpty(name)) return false;
            return CallOrThrow("destroy", name) == "OK";
        }

        /// <summary>Plays a sound from the project sounds folder, for example "jump.ogg".</summary>
        public static bool PlaySound(string sound)
        {
            if (string.IsNullOrEmpty(sound)) return false;
            return CallOrThrow("playSound", sound) == "OK";
        }

        /// <summary>Stops a sound if it is playing.</summary>
        public static bool StopSound(string sound)
        {
            if (string.IsNullOrEmpty(sound)) return false;
            return CallOrThrow("stopSound", sound) == "OK";
        }

        public static bool PauseSound(string sound)
        {
            if (string.IsNullOrEmpty(sound)) return false;
            return CallOrThrow("pauseSound", sound) == "OK";
        }

        public static bool LoopSound(string sound, bool loop)
        {
            if (string.IsNullOrEmpty(sound)) return false;
            return CallOrThrow("loopSound", sound, "", loop ? 1f : 0f) == "OK";
        }

        public static void Log(object message)
        {
            NativeLog("info", message == null ? "" : message.ToString());
        }

        public static void LogWarning(object message)
        {
            NativeLog("warn", message == null ? "" : message.ToString());
        }

        public static void LogError(object message)
        {
            NativeLog("error", message == null ? "" : message.ToString());
        }

        internal static string Call(string command, string a = "", string b = "", float value = 0f)
        {
            try
            {
                return NativeCall(command, a ?? "", b ?? "", value) ?? "";
            }
            catch (Exception error)
            {
                return "ERR:" + error.Message;
            }
        }

        internal static string CallOrThrow(string command, string a = "", string b = "", float value = 0f)
        {
            string result = Call(command, a, b, value);
            if (result.StartsWith("ERR:", StringComparison.Ordinal))
                throw new InvalidOperationException(result.Substring(4));
            return result;
        }

        internal static float Float(string entity, string property)
        {
            return ToFloat(CallOrThrow("getFloat", entity, property), 0f);
        }

        internal static void SetFloat(string entity, string property, float value)
        {
            CallOrThrow("setFloat", entity, property, value);
        }

        internal static bool Bool(string entity, string property)
        {
            return CallOrThrow("getBool", entity, property) == "1";
        }

        internal static void SetBool(string entity, string property, bool value)
        {
            CallOrThrow("setBool", entity, property, value ? 1f : 0f);
        }

        internal static float ToFloat(string value, float fallback)
        {
            return float.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out float result)
                    ? result
                    : fallback;
        }
    }

    /// <summary>A scene element (sprite, body, model, light, camera...).</summary>
    public sealed class Entity
    {
        internal Entity(string name)
        {
            Name = name;
        }

        public string Name { get; }

        public bool Exists => Engine.Bool(Name, "exists");

        public float X
        {
            get => Engine.Float(Name, "x");
            set => Engine.SetFloat(Name, "x", value);
        }

        public float Y
        {
            get => Engine.Float(Name, "y");
            set => Engine.SetFloat(Name, "y", value);
        }

        public float Z
        {
            get => Engine.Float(Name, "z");
            set => Engine.SetFloat(Name, "z", value);
        }

        public Vector2 Position
        {
            get => new Vector2(X, Y);
            set { X = value.X; Y = value.Y; }
        }

        public Vector3 Position3D
        {
            get => new Vector3(X, Y, Z);
            set { X = value.X; Y = value.Y; Z = value.Z; }
        }

        public float Rotation
        {
            get => Engine.Float(Name, "rotation");
            set => Engine.SetFloat(Name, "rotation", value);
        }

        public float RotationX
        {
            get => Engine.Float(Name, "rotationX");
            set => Engine.SetFloat(Name, "rotationX", value);
        }

        public float RotationY
        {
            get => Engine.Float(Name, "rotationY");
            set => Engine.SetFloat(Name, "rotationY", value);
        }

        public float RotationZ
        {
            get => Engine.Float(Name, "rotationZ");
            set => Engine.SetFloat(Name, "rotationZ", value);
        }

        public float Scale
        {
            get => Engine.Float(Name, "scale");
            set => Engine.SetFloat(Name, "scale", value);
        }

        public float ScaleX
        {
            get => Engine.Float(Name, "scaleX");
            set => Engine.SetFloat(Name, "scaleX", value);
        }

        public float ScaleY
        {
            get => Engine.Float(Name, "scaleY");
            set => Engine.SetFloat(Name, "scaleY", value);
        }

        public float ScaleZ
        {
            get => Engine.Float(Name, "scaleZ");
            set => Engine.SetFloat(Name, "scaleZ", value);
        }

        public float Width => Engine.Float(Name, "width");

        public float Height => Engine.Float(Name, "height");

        public float Alpha
        {
            get => Engine.Float(Name, "alpha");
            set => Engine.SetFloat(Name, "alpha", value);
        }

        public bool Visible
        {
            get => Engine.Bool(Name, "visible");
            set => Engine.SetBool(Name, "visible", value);
        }

        public bool Is3D => Engine.Bool(Name, "3d");

        public void SetPosition(float x, float y)
        {
            X = x;
            Y = y;
        }

        public void SetPosition(float x, float y, float z)
        {
            X = x;
            Y = y;
            Z = z;
        }

        public void Move(float x, float y)
        {
            X += x;
            Y += y;
        }

        public void Move(float x, float y, float z)
        {
            X += x;
            Y += y;
            Z += z;
        }

        public void SetRotation(float x, float y, float z)
        {
            RotationX = x;
            RotationY = y;
            RotationZ = z;
        }

        public void SetScale(float x, float y, float z)
        {
            ScaleX = x;
            ScaleY = y;
            ScaleZ = z;
        }

        public void Destroy()
        {
            Engine.Destroy(Name);
        }

        public override string ToString()
        {
            return Name;
        }
    }

    /// <summary>Touch and keyboard state of the running game.</summary>
    public sealed class Input
    {
        /// <summary>LibGDX key name, for example "A", "SPACE", "LEFT" or "W".</summary>
        public bool IsKeyDown(string key) => Engine.CallOrThrow("key", key) == "1";

        public bool IsKeyDown(int keyCode) => Engine.CallOrThrow("keyCode", "", "", keyCode) == "1";

        public bool IsKeyJustPressed(string key) => Engine.CallOrThrow("keyPressed", key) == "1";

        public bool IsKeyJustPressed(int keyCode) => Engine.CallOrThrow("keyCodePressed", "", "", keyCode) == "1";

        public bool IsButtonPressed(int button) => Engine.CallOrThrow("button", "", "", button) == "1";

        public bool IsTouched => Engine.CallOrThrow("touched") == "1";

        public bool JustTouched => Engine.CallOrThrow("justTouched") == "1";

        public float TouchX => Engine.ToFloat(Engine.CallOrThrow("touchX"), 0f);

        public float TouchY => Engine.ToFloat(Engine.CallOrThrow("touchY"), 0f);
    }

    public struct Vector2
    {
        public float X;
        public float Y;

        public Vector2(float x, float y)
        {
            X = x;
            Y = y;
        }

        public static Vector2 Zero => new Vector2(0f, 0f);

        public override string ToString()
        {
            return "(" + X.ToString(CultureInfo.InvariantCulture) + ", "
                    + Y.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }

    public struct Vector3
    {
        public float X;
        public float Y;
        public float Z;

        public Vector3(float x, float y, float z)
        {
            X = x;
            Y = y;
            Z = z;
        }

        public static Vector3 Zero => new Vector3(0f, 0f, 0f);

        public override string ToString()
        {
            return "(" + X.ToString(CultureInfo.InvariantCulture) + ", "
                    + Y.ToString(CultureInfo.InvariantCulture) + ", "
                    + Z.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
