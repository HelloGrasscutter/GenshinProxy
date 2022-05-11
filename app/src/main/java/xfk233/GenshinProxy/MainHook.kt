package xfk233.GenshinProxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findConstructor
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.regex.Pattern


class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val regex = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob)\\.com")
    private lateinit var server: String
    private var forceUrl = false
    private lateinit var modulePath: String
    private lateinit var moduleRes: XModuleResources

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.miHoYo.GenshinImpact") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        findMethod(Application::class.java, true) { name == "attachBaseContext" }.hookBefore {
            val context = it.args[0] as Context
            val sp = context.getSharedPreferences("serverConfig", 0)
            forceUrl = sp.getBoolean("forceUrl", false)
            server = sp.getString("serverip", "") ?: ""
        }
        sslHook(lpparam)
        hook()
        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore { param ->
            val context = param.thisObject as Activity
            val sp = context.getSharedPreferences("serverConfig", 0)
            AlertDialog.Builder(context).apply {
                setCancelable(false)
                setTitle(moduleRes.getString(R.string.SelectServer))
                setMessage(moduleRes.getString(R.string.Tips))
                setView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(EditText(context).apply {
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
                    addView(Switch(context).apply {
                        text = moduleRes.getString(R.string.ForcedMode)
                        isChecked = sp.getBoolean("forceUrl", false)
                        setOnClickListener {
                            sp.edit().run {
                                putBoolean("forceUrl", (it as Switch).isChecked)
                                apply()
                            }
                            forceUrl = (it as Switch).isChecked
                        }
                    })
                })
                setNegativeButton(moduleRes.getString(R.string.CustomServer)) { _, _ ->
                    val ip = sp.getString("serverip", "") ?: ""
                    if (ip == "") {
                        Toast.makeText(context, moduleRes.getString(R.string.ServerAddressError), Toast.LENGTH_LONG).show()
                        context.finish()
                    } else {
                        server = ip
                        forceUrl = true
                    }
                }
                setNeutralButton(moduleRes.getString(R.string.OfficialServer)) { _, _ ->
                    forceUrl = false
                    server = ""
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
            replaceUrl(it, 1)
        }

        findMethod("okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }

        findMethod("com.google.gson.Gson") { name == "fromJson" && parameterTypes[0] == String::class.java && parameterTypes[1] == java.lang.reflect.Type::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findConstructor("java.net.URL") { parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
    }

    private fun replaceUrl(method: XC_MethodHook.MethodHookParam, args: Int) {
        if (!forceUrl) return
        if (!this::server.isInitialized) return
        if (server == "") return

        if (BuildConfig.DEBUG) XposedBridge.log("old: " + method.args[args].toString())
        val m = regex.matcher(method.args[args].toString())
        if (m.find()) {
            method.args[args] = m.replaceAll(server)
        }
        if (BuildConfig.DEBUG) XposedBridge.log("new: " + method.args[args].toString())
    }
}
