using System;
using System.Runtime.CompilerServices;

namespace Nebulon
{
    public static class Engine
    {
        [MethodImpl(MethodImplOptions.InternalCall)]
        private static extern void NativeLog(string level, string message);

        public static string Version => "1.0.6";

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
    }
}
