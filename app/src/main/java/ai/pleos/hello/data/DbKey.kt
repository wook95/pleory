package ai.pleos.hello.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SQLCipher DB 비밀번호(보안 가이드: 키를 하드코딩하지 않는다).
 * 처음 한 번 무작위 32바이트를 만들고, Android Keystore 의 AES-GCM 키로 감싸 SharedPreferences 에 둔다.
 * 감싼 값이 없거나 풀 수 없으면 기존 DB 파일(평문 개발 데이터 또는 키를 잃은 암호문)은 열 수 없으므로 지우고 새로 만든다.
 */
object DbKey {
    private const val ALIAS = "pleory_db_key"
    private const val PREFS = "db_key"
    private const val KEY_WRAPPED = "wrapped"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun passphrase(context: Context, dbName: String): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_WRAPPED, null)?.let { wrapped ->
            runCatching { return unwrap(wrapped) }
                .onFailure { Log.w("PleoryDb", "db key unwrap failed: ${it.javaClass.simpleName}") }
        }
        // 지금 있는 DB 는 이 키로 열 수 없다(첫 실행이면 암호화 전 평문 DB)
        if (context.deleteDatabase(dbName)) Log.i("PleoryDb", "old db removed (no usable key)")
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // 0 바이트가 섞이지 않게 hex 문자열을 비밀번호로 쓴다
        val pass = raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
        check(prefs.edit().putString(KEY_WRAPPED, wrap(pass)).commit()) { "db key save failed" }
        return pass
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    private fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
        return cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES)
    }
}
