package com.jinsim.account.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jinsim.account.AccountRepository
import com.jinsim.account.JslApi
import java.util.concurrent.Executors

/**
 * JinSim 主账号登录页（只做登录/注册，不再承载账号列表）。
 *
 * 账号列表回到底包原生的「账户」页：绑定的微软账号会直接出现在那一页，
 * 点一下即选中并用于启动。这里只解决一件事：绑定微软之前需要一个 JinSim 主账号。
 * 从网页深链 jsl://tp?pages=bindms 进来时，登录成功会自动续上微软授权。
 */
class PersonalCenterActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_BIND = "then_bind"

        @JvmStatic
        fun launch(context: Context) {
            context.startActivity(Intent(context, PersonalCenterActivity::class.java))
        }

        @JvmStatic
        fun launchForBind(context: Context) {
            context.startActivity(
                Intent(context, PersonalCenterActivity::class.java).putExtra(EXTRA_BIND, true)
            )
        }
    }

    private lateinit var repo: AccountRepository
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView
    private lateinit var form: LinearLayout
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var logoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AccountRepository.get(this)
        repo.hardResetOnce(this, "jsl_account_wipe_v2")
        setContentView(buildUi())
        paint()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "JinSim 账号"
            textSize = 22f
            setTextColor(Color.parseColor("#1B1B22"))
        })

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#6B7080"))
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(statusText)

        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        emailInput = EditText(this).apply {
            hint = "邮箱"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        passwordInput = EditText(this).apply {
            hint = "密码（至少 8 位）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        loginButton = Button(this).apply {
            text = "登录"
            setOnClickListener { submit(register = false) }
        }
        registerButton = Button(this).apply {
            text = "注册新账号"
            setOnClickListener { submit(register = true) }
        }

        form.addView(emailInput)
        form.addView(passwordInput)
        form.addView(loginButton)
        form.addView(registerButton)
        root.addView(form)

        logoutButton = Button(this).apply {
            text = "退出登录"
            visibility = View.GONE
            setOnClickListener { confirmLogout() }
        }
        root.addView(logoutButton)

        root.addView(TextView(this).apply {
            text = "登录后，绑定的微软账号会直接出现在启动器的「账户」页，点一下即可选中启动。"
            textSize = 12f
            setTextColor(Color.parseColor("#8A8F9E"))
            gravity = Gravity.START
            setPadding(0, dp(20), 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun paint() {
        val account = repo.jinsim()
        if (account == null) {
            statusText.text = "未登录。登录后才能绑定微软账号。"
            form.visibility = View.VISIBLE
            logoutButton.visibility = View.GONE
        } else {
            statusText.text = "${account.displayName} · ${account.email}"
            form.visibility = View.GONE
            logoutButton.visibility = View.VISIBLE
            maybeContinueBind()
        }
    }

    private fun submit(register: Boolean) {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isEmpty() || password.length < 8) {
            toast("邮箱不能为空，密码至少 8 位")
            return
        }
        setBusy(true)
        io.execute {
            try {
                val result = if (register) JslApi.register(email, password, null) else JslApi.login(email, password)
                runOnUiThread {
                    repo.setJinsim(result.account, result.token)
                    repo.replaceFromCloud(result.accounts)
                    setBusy(false)
                    toast(if (register) "注册成功" else "登录成功")
                    paint()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    toast(e.message ?: "登录失败")
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        loginButton.isEnabled = !busy
        registerButton.isEnabled = !busy
        loginButton.text = if (busy) "登录中…" else "登录"
    }

    /** 从「添加微软账号」或网页深链进来时，登录完成直接续上 WebView 授权 */
    private fun maybeContinueBind() {
        if (intent?.getBooleanExtra(EXTRA_BIND, false) == true && !repo.sessionToken().isNullOrBlank()) {
            intent?.removeExtra(EXTRA_BIND)
            startActivity(Intent(this, MsaLoginActivity::class.java))
            finish()
        }
    }

    private fun confirmLogout() {
        repo.logout()
        toast("已退出")
        paint()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
