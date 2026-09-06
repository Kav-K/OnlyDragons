using System;
using System.IO;
using System.Net.Sockets;
using System.Text;

public static class MinecraftStatus {
    static void WriteVarInt(Stream s, int value) {
        uint v = unchecked((uint)value);
        do { byte b = (byte)(v & 127); v >>= 7; if (v != 0) b |= 128; s.WriteByte(b); } while (v != 0);
    }
    static int ReadVarInt(Stream s) {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int b = s.ReadByte(); if (b < 0) throw new EndOfStreamException();
            result |= (b & 127) << (7 * i);
            if ((b & 128) == 0) return result;
        }
        throw new IOException("Invalid Minecraft VarInt");
    }
    public static string Query(int port) {
        using (var client = new TcpClient()) {
            var connect = client.ConnectAsync("127.0.0.1", port);
            if (!connect.Wait(5000)) throw new IOException("Minecraft connection timed out");
            using (var s = client.GetStream()) {
                s.ReadTimeout = 5000; s.WriteTimeout = 5000;
                using (var packet = new MemoryStream()) {
                    WriteVarInt(packet, 0); WriteVarInt(packet, 47);
                    byte[] host = Encoding.UTF8.GetBytes("127.0.0.1");
                    WriteVarInt(packet, host.Length); packet.Write(host, 0, host.Length);
                    packet.WriteByte((byte)(port >> 8)); packet.WriteByte((byte)port);
                    WriteVarInt(packet, 1);
                    WriteVarInt(s, (int)packet.Length); packet.Position = 0; packet.CopyTo(s);
                }
                s.WriteByte(1); s.WriteByte(0); s.Flush();
                int size = ReadVarInt(s);
                if (size < 2 || size > 1048576 || ReadVarInt(s) != 0) throw new IOException("Invalid status packet");
                int length = ReadVarInt(s);
                if (length < 1 || length > size) throw new IOException("Invalid status text length");
                byte[] json = new byte[length]; int offset = 0;
                while (offset < length) {
                    int read = s.Read(json, offset, length - offset);
                    if (read == 0) throw new EndOfStreamException(); offset += read;
                }
                return Encoding.UTF8.GetString(json);
            }
        }
    }
}
