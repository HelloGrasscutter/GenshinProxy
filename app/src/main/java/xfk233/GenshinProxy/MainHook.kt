package xfk233.GenshinProxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.init.InitFields
import com.github.kyuubiran.ezxhelper.utils.findConstructor
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.regex.Pattern
import kotlin.system.exitProcess


class MainHook : IXposedHookLoadPackage {
    private val regex = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob)\\.com")
    private lateinit var server: String

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.miHoYo.GenshinImpact") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore {
            val context = it.thisObject as Activity
            val sp = context.getPreferences(0)
            AlertDialog.Builder(context).apply {
                setTitle("Select server / 选择服务器")
                setMessage("Input server address / 请输入服务器地址: ")
                setView(EditText(context).apply {
                    hint = "http(s)://server.com:1234"
                    val str = sp.getString("serverip", "") ?: ""
                    setText(str.toCharArray(), 0, str.length)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {}

                        @SuppressLint("CommitPrefEdits")
                        override fun afterTextChanged(p0: Editable) {
                            sp.edit().run {
                                putString("serverip", p0.toString())
                                apply()
                            }
                        }
                    })
                })
                setNegativeButton("Custom server / 自定义服务器") { _, _ ->
                    val ip = sp.getString("serverip", "") ?: ""
                    if (ip == "") {
                        Toast.makeText(context, "Server address error.", Toast.LENGTH_LONG).show()
                        exitProcess(1)
                    } else {
                        server = ip
                    }
                    sslHook(lpparam)
                    hook()
                }
                setNeutralButton("Official server / 官方服务器") { _, _ ->
                }
            }.show()
        }
    }

    private fun sslHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        findMethodOrNull("com.combosdk.lib.third.okhttp3.internal.tls.OkHostnameVerifier") { name == "verify" }?.hookBefore {
            it.result = true
        }
        findMethodOrNull("com.combosdk.lib.third.okhttp3.CertificatePinner") { name == "check" && parameterTypes[0] == String::class.java && parameterTypes[1] == List::class.java }?.hookBefore {
            it.result = null
        }
        JustTrustMe().hook(lpparam)
    }

    private fun hook() {
        findMethod("com.miHoYo.sdk.webview.MiHoYoWebview") { name == "load" && parameterTypes[0] == String::class.java && parameterTypes[1] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[1].toString())
            val m = regex.matcher(it.args[1].toString())
            if (m.find()) {
                it.args[1] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[1].toString())
        }

        findMethod("okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }
        findMethod("com.combosdk.lib.third.okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }

        findMethod("com.google.gson.Gson") { name == "fromJson" && parameterTypes[0] == String::class.java && parameterTypes[1] == java.lang.reflect.Type::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }
        findConstructor("java.net.URL") { parameterTypes[0] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }
        findMethod("com.combosdk.lib.third.okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }
        findMethod("okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            XposedBridge.log("old: " + it.args[0].toString())
            val m = regex.matcher(it.args[0].toString())
            if (m.find()) {
                it.args[0] = m.replaceAll(server)
            }
            XposedBridge.log("new: " + it.args[0].toString())
        }
    }
}
