package com.imagegrabber.app

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object AdminConfig {
    const val EMAIL = "bilalalmomani90@gmail.com"
}

enum class AuthResult { OK, BAD, LOCKED }

/**
 * حماية محلية للوحة التحكم: بريد المشرف + رمز (PIN) يُنشأ عند أول دخول.
 * ملاحظة: هذا لا يتحقق من ملكية البريد (لا يوجد تسجيل دخول سحابي)؛ الحماية الفعلية هي الرمز.
 */
class AdminAuth(ctx: Context) {
    private val p = ctx.getSharedPreferences("admin", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = p.contains("hash")

    private fun hash(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8))
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        p.edit()
            .putString("salt", enc.encodeToString(salt))
            .putString("hash", enc.encodeToString(hash(salt, pin)))
            .putInt("fails", 0)
            .putLong("lock", 0L)
            .apply()
    }

    fun verify(email: String, pin: String): AuthResult {
        val now = System.currentTimeMillis()
        if (p.getLong("lock", 0L) > now) return AuthResult.LOCKED
        val dec = Base64.getDecoder()
        val salt = try { dec.decode(p.getString("salt", "").orEmpty()) } catch (e: IllegalArgumentException) { ByteArray(0) }
        val stored = try { dec.decode(p.getString("hash", "").orEmpty()) } catch (e: IllegalArgumentException) { ByteArray(0) }
        val ok = email.trim().equals(AdminConfig.EMAIL, ignoreCase = true) &&
            MessageDigest.isEqual(hash(salt, pin), stored)
        if (ok) {
            p.edit().putInt("fails", 0).apply()
            return AuthResult.OK
        }
        val fails = p.getInt("fails", 0) + 1
        if (fails >= 5) {
            p.edit().putInt("fails", 0).putLong("lock", now + 5 * 60_000L).apply()
        } else {
            p.edit().putInt("fails", fails).apply()
        }
        return AuthResult.BAD
    }
}
