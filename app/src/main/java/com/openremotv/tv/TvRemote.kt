package com.openremotv.tv

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The phone as the TV's microphone, through the TV's own remote service (the one Google's TV remote app uses), not
 * ADB. Pairing happens once: the TV shows a six-character code, typed on the phone. After that, [talk] presses the
 * search key, the TV opens its voice search (Google Assistant or Gemini) listening to this phone, and the phone's
 * mic streams to it until the person stops.
 *
 * The phone's key lives in the Android keystore and never leaves it; the TV's certificate is accepted on first
 * use, as the TV's own remote app does. Messages are protobuf, written by hand here (a handful of fields) rather
 * than with a protobuf library.
 */
class TvRemote(private val context: Context, private val host: () -> String) {

    private val prefs = context.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    /** True once this phone was paired with the TV at the current address. */
    val paired: Boolean get() = prefs.getString("paired_host", null) == host()

    // ---- pairing (port 6467) --------------------------------------------------------------------------------

    private var pairing: SSLSocket? = null

    /** Starts pairing: the TV now shows a code. */
    suspend fun startPairing() = withContext(Dispatchers.IO) {
        pairing?.close()
        val s = connect(PAIRING_PORT)
        pairing = s
        send(s, outer(10, Proto().string(1, "openremotv").string(2, "OpenRemoTV")))            // pairing_request
        expect(s, 11)                                                                         // pairing_request_ack
        val encoding = Proto().varint(1, 3).varint(2, 6)                                      // hexadecimal, 6 symbols
        send(s, outer(20, Proto().message(1, encoding).varint(3, 1)))                         // options: input role
        expect(s, 20)                                                                         // options
        send(s, outer(30, Proto().message(1, encoding).varint(2, 1)))                         // configuration
        expect(s, 31)                                                                         // configuration_ack
    }

    /** Finishes pairing with the code the TV shows. Returns false when the code is wrong. */
    suspend fun finishPairing(code: String): Boolean = withContext(Dispatchers.IO) {
        val s = pairing ?: error("pairing wasn't started")
        val c = code.trim().uppercase()
        require(c.length == 6 && c.all { it in "0123456789ABCDEF" }) { "the code has six letters and numbers" }
        val client = clientCertificate().publicKey as RSAPublicKey
        val server = (s.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(magnitude(client.modulus))
        sha.update(hexBytes("0" + client.publicExponent.toString(16)))
        sha.update(magnitude(server.modulus))
        sha.update(hexBytes("0" + server.publicExponent.toString(16)))
        sha.update(hexBytes(c.substring(2)))
        val secret = sha.digest()
        // The code's first byte is a check on the rest: a typo is caught here, before the TV sees anything.
        if ((secret[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) return@withContext false
        send(s, outer(40, Proto().bytes(1, secret)))                                          // secret
        expect(s, 41)                                                                         // secret_ack
        s.close()
        pairing = null
        prefs.edit().putString("paired_host", host()).apply()
        true
    }

    // ---- voice (port 6466) ----------------------------------------------------------------------------------

    @Volatile private var talking = false

    /** Stops the voice session [talk] is streaming. */
    fun stopTalking() {
        talking = false
    }

    /**
     * Opens the TV's voice search and streams this phone's mic to it until [stopTalking] (or 15 seconds).
     * Needs RECORD_AUDIO. [listening] is called once the TV is ready for sound.
     */
    @SuppressLint("MissingPermission")
    suspend fun talk(listening: () -> Unit) = withContext(Dispatchers.IO) {
        val s = connect(REMOTE_PORT)
        try {
            val input = s.inputStream
            val out = s.outputStream
            var active = FEATURES
            var session = -1
            // Settle the connection: configure, set active, and answer pings, until the TV is ready.
            val deadline = System.currentTimeMillis() + 8_000
            var keySent = false
            while (session < 0 && System.currentTimeMillis() < deadline) {
                val msg = Proto.read(read(input))
                when {
                    msg.has(1) -> {                                                           // remote_configure
                        active = FEATURES and Proto.read(msg.bytes(1)).int(1)
                        val info = Proto().string(1, "OpenRemoTV").string(2, "OpenRemoTV").varint(3, 1)
                            .string(4, "1").string(5, "com.openremotv").string(6, "1")
                        write(out, Proto().message(1, Proto().varint(1, active.toLong()).message(2, info)))
                    }
                    msg.has(2) -> write(out, Proto().message(2, Proto().varint(1, active.toLong())))   // set_active
                    msg.has(8) -> write(out, Proto().message(9,                                          // ping
                        Proto().varint(1, Proto.read(msg.bytes(8)).int(1).toLong())))
                    msg.has(30) -> session = Proto.read(msg.bytes(30)).int(1)                            // voice_begin
                }
                if (!keySent && msg.has(2)) {
                    // Active: press search, and the TV asks for a voice session.
                    write(out, Proto().message(10, Proto().varint(1, KEYCODE_SEARCH).varint(2, 3)))
                    keySent = true
                }
            }
            check(session >= 0) { "the TV didn't open its voice search" }
            write(out, Proto().message(30, Proto().varint(1, session.toLong())))
            stream(out, session, listening)
            write(out, Proto().message(32, Proto().varint(1, session.toLong())))
            Thread.sleep(300)
        } finally {
            talking = false
            runCatching { s.close() }
        }
    }

    /** 16-bit PCM, 8 kHz mono, as the TV expects, in 20 KB messages (at least 8 KB each). */
    @SuppressLint("MissingPermission")
    private fun stream(out: OutputStream, session: Int, listening: () -> Unit) {
        val rate = 8000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK * 2))
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "the phone's microphone is busy" }
        val buf = ByteArray(CHUNK)
        val until = System.currentTimeMillis() + 15_000
        talking = true
        rec.startRecording()
        listening()
        try {
            while (talking && System.currentTimeMillis() < until) {
                var filled = 0
                // A quarter of a second at a time keeps the TV's live captions moving.
                while (filled < MIN_CHUNK && talking) {
                    val n = rec.read(buf, filled, MIN_CHUNK - filled)
                    if (n <= 0) break
                    filled += n
                }
                if (filled == 0) continue
                val samples = if (filled < MIN_CHUNK) buf.copyOf(MIN_CHUNK) else buf.copyOf(filled)
                write(out, Proto().message(31, Proto().varint(1, session.toLong()).bytes(2, samples)))
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }

    // ---- TLS, certificates, framing ---------------------------------------------------------------------------

    private fun connect(port: Int): SSLSocket {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        clientCertificate()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, null) }
        // The TV's certificate is self-signed: accept it, as the TV's own remote app does.
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null) }
        val s = ctx.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host(), port), 5_000)
        s.soTimeout = 10_000
        s.startHandshake()
        return s
    }

    /** This phone's certificate, made once in the Android keystore (the private key can't be read out). */
    private fun clientCertificate(): X509Certificate {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getCertificate(ALIAS) as? X509Certificate)?.let { return it }
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        gen.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512,
                KeyProperties.DIGEST_NONE)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setCertificateSubject(X500Principal("CN=OpenRemoTV"))
            .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 20L * 365 * 86_400_000))
            .build())
        gen.generateKeyPair()
        return ks.getCertificate(ALIAS) as X509Certificate
    }

    private fun outer(field: Int, body: Proto) = Proto().varint(1, 2).varint(2, 200).message(field, body)

    private fun send(s: SSLSocket, msg: Proto) = write(s.outputStream, msg)

    /** Reads pairing messages until one carries {@code field}; a status other than OK is a refusal. */
    private fun expect(s: SSLSocket, field: Int) {
        val msg = Proto.read(read(s.inputStream))
        val status = msg.int(2)
        check(status == 200) { "the TV refused pairing ($status)" }
        check(msg.has(field)) { "the TV answered something unexpected" }
    }

    private fun write(out: OutputStream, msg: Proto) {
        val body = msg.toByteArray()
        synchronized(out) {
            out.write(Proto.varintBytes(body.size.toLong()))
            out.write(body)
            out.flush()
        }
    }

    private fun read(input: InputStream): ByteArray {
        var len = 0
        var shift = 0
        while (true) {
            val b = input.read()
            check(b >= 0) { "the TV closed the connection" }
            len = len or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        val buf = ByteArray(len)
        var at = 0
        while (at < len) {
            val n = input.read(buf, at, len - at)
            check(n > 0) { "the TV closed the connection" }
            at += n
        }
        return buf
    }

    companion object {
        private const val PAIRING_PORT = 6467
        private const val REMOTE_PORT = 6466
        private const val ALIAS = "openremotv-tv-remote"
        private const val KEYCODE_SEARCH = 84L
        private const val CHUNK = 20 * 1024
        private const val MIN_CHUNK = 8 * 1024
        /** Ping, keys, voice, power, volume. */
        private const val FEATURES = 1 or 2 or 8 or 32 or 64

        /** A positive number's bytes, without the sign byte Java adds. */
        fun magnitude(n: BigInteger): ByteArray {
            val b = n.toByteArray()
            return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
        }

        /** Hex to bytes, as the TV's own pairing code does it (an odd length gets a leading zero). */
        fun hexBytes(hex: String): ByteArray {
            val h = if (hex.length % 2 == 1) "0$hex" else hex
            return ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}

/** The few protobuf pieces this protocol needs: varints, strings, bytes and nested messages. */
class Proto {
    private val out = ByteArrayOutputStream()
    private val fields = HashMap<Int, Any>()

    fun varint(field: Int, value: Long) = apply { tag(field, 0); out.write(varintBytes(value)) }
    fun string(field: Int, value: String) = bytes(field, value.toByteArray())
    fun bytes(field: Int, value: ByteArray) = apply { tag(field, 2); out.write(varintBytes(value.size.toLong())); out.write(value) }
    fun message(field: Int, value: Proto) = bytes(field, value.toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, type: Int) = out.write(varintBytes(((field shl 3) or type).toLong()))

    fun has(field: Int) = fields.containsKey(field)
    fun int(field: Int): Int = (fields[field] as? Long)?.toInt() ?: 0
    fun bytes(field: Int): ByteArray = fields[field] as? ByteArray ?: ByteArray(0)

    companion object {
        fun varintBytes(value: Long): ByteArray {
            val b = ByteArrayOutputStream()
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    b.write(v.toInt())
                    return b.toByteArray()
                }
                b.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }

        /** Top-level fields only; nested messages stay as bytes for their own [read]. */
        fun read(data: ByteArray): Proto {
            val p = Proto()
            var i = 0
            fun varint(): Long {
                var r = 0L
                var shift = 0
                while (true) {
                    val b = data[i++].toInt() and 0xFF
                    r = r or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return r
                    shift += 7
                }
            }
            while (i < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> p.fields[field] = varint()
                    2 -> {
                        val n = varint().toInt()
                        p.fields[field] = data.copyOfRange(i, i + n)
                        i += n
                    }
                    5 -> i += 4
                    1 -> i += 8
                    else -> return p
                }
            }
            return p
        }
    }
}
