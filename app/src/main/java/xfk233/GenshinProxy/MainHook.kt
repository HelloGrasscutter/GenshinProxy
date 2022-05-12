package xfk233.GenshinProxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.XModuleResources
import android.graphics.Color
import android.graphics.PixelFormat
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import xfk233.GenshinProxy.Utils.dp2px
import xfk233.GenshinProxy.Utils.isInit
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.system.exitProcess


class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val regex = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob)\\.com")
    private lateinit var server: String
    private var forceUrl = false
    private lateinit var modulePath: String
    private lateinit var moduleRes: XModuleResources
    private lateinit var windowManager: WindowManager
    private lateinit var activity: Activity
    private var proxyList = false
    private lateinit var sp: SharedPreferences
    private val proxyListRegex = arrayListOf(
        "api-os-takumi.mihoyo.com",
        "hk4e-api-os-static.mihoyo.com",
        "hk4e-sdk-os.mihoyo.com",
        "dispatchosglobal.yuanshen.com",
        "osusadispatch.yuanshen.com",
        "account.mihoyo.com",
        "log-upload-os.mihoyo.com",
        "dispatchcntest.yuanshen.com",
        "devlog-upload.mihoyo.com",
        "webstatic.mihoyo.com",
        "log-upload.mihoyo.com",
        "hk4e-sdk.mihoyo.com",
        "api-beta-sdk.mihoyo.com",
        "api-beta-sdk-os.mihoyo.com",
        "cnbeta01dispatch.yuanshen.com",
        "dispatchcnglobal.yuanshen.com",
        "cnbeta02dispatch.yuanshen.com",
        "sdk-os-static.mihoyo.com",
        "webstatic-sea.mihoyo.com",
        "webstatic-sea.hoyoverse.com",
        "hk4e-sdk-os-static.hoyoverse.com",
        "sdk-os-static.hoyoverse.com",
        "api-account-os.hoyoverse.com",
        "hk4e-sdk-os.hoyoverse.com",
        "overseauspider.yuanshen.com",
        "gameapi-account.mihoyo.com",
        "minor-api.mihoyo.com",
        "public-data-api.mihoyo.com",
        "uspider.yuanshen.com",
        "sdk-static.mihoyo.com",
        "minor-api-os.hoyoverse.com",
        "log-upload-os.hoyoverse.com"
    )

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    @SuppressLint("WrongConstant", "ClickableViewAccessibility")
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.miHoYo.GenshinImpact") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        findMethod("com.combosdk.openapi.ComboApplication") { name == "attachBaseContext" }.hookBefore {
            val context = it.args[0] as Context
            sp = context.getSharedPreferences("serverConfig", 0)
            forceUrl = sp.getBoolean("forceUrl", false)
            server = sp.getString("serverip", "") ?: ""
            proxyList = sp.getBoolean("ProxyList", false)
        }
        sslHook(lpparam)
        hook()
        findMethod(Activity::class.java, true) { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
        }
        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
            showDialog()
        }
    }

    private fun showDialog() {
        AlertDialog.Builder(activity).apply {
            setCancelable(false)
            setTitle(moduleRes.getString(R.string.SelectServer))
            setMessage(moduleRes.getString(R.string.Tips))
            setNegativeButton(moduleRes.getString(R.string.Settings)) {_, _ ->
                AlertDialog.Builder(activity).apply {
                    setMessage(moduleRes.getString(R.string.Tips2))
                    setCancelable(false)
                    setView(ScrollView(context).apply {
                        addView(LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(EditText(activity).apply {
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
                            addView(Switch(activity).apply {
                                text = moduleRes.getString(R.string.ForcedMode)
                                isChecked = sp.getBoolean("forceUrl", false)
                                setOnCheckedChangeListener { _, b ->
                                    sp.edit().run {
                                        putBoolean("forceUrl", b)
                                        apply()
                                    }
                                    forceUrl = b
                                }
                            })
                            addView(Switch(activity).apply {
                                text = moduleRes.getString(R.string.ProxyList)
                                isChecked = sp.getBoolean("ProxyList", false)
                                setOnCheckedChangeListener { _, b ->
                                    sp.edit().run {
                                        putBoolean("ProxyList", b)
                                        apply()
                                    }
                                    proxyList = b
                                }
                            })
                            addView(Switch(activity).apply {
                                text = moduleRes.getString(R.string.HookConfig)
                                isChecked = sp.getBoolean("HookConfig", false)
                                setOnCheckedChangeListener { _, b ->
                                    sp.edit().run {
                                        putBoolean("HookConfig", b)
                                        apply()
                                    }
                                    proxyList = b
                                }
                            })
                            addView(Switch(activity).apply {
                                text = moduleRes.getString(R.string.EnableTools)
                                isChecked = sp.getBoolean("EnableTools", false)
                                setOnCheckedChangeListener { _, b ->
                                    sp.edit().run {
                                        putBoolean("EnableTools", b)
                                        apply()
                                    }
                                    proxyList = b
                                }
                            })
                        })
                    })
                    setPositiveButton(moduleRes.getString(R.string.Back)) { _, _ ->
                        showDialog()
                    }
                    setNeutralButton(moduleRes.getString(R.string.ExitGames)) {_, _ ->
                        exitProcess(0)
                    }
                }.show()
            }
            setPositiveButton(moduleRes.getString(R.string.CustomServer)) { _, _ ->
                val ip = sp.getString("serverip", "") ?: ""
                if (ip == "") {
                    Toast.makeText(activity, moduleRes.getString(R.string.ServerAddressError), Toast.LENGTH_LONG).show()
                    activity.finish()
                } else {
                    server = ip
                    forceUrl = true
                    if (sp.getBoolean("EnableTools", false)) gmTool()
                }
            }
            setNeutralButton(moduleRes.getString(R.string.OfficialServer)) { _, _ ->
                forceUrl = false
                server = ""
            }
        }.show()
    }

    inner class MoveOnTouchListener : View.OnTouchListener {
        private var originalXPos = 0
        private var originalYPos = 0

        private var offsetX = 0f
        private var offsetY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.rawX
                    val y = event.rawY

                    val location = IntArray(2)
                    v.getLocationOnScreen(location)

                    originalXPos = location[0]
                    originalYPos = location[1]

                    offsetX = x - originalXPos
                    offsetY = y - originalYPos
                }
                MotionEvent.ACTION_MOVE -> {
                    val onScreen = IntArray(2)
                    v.getLocationOnScreen(onScreen)

                    val x = event.rawX
                    val y = event.rawY

                    val params: WindowManager.LayoutParams =
                        v.layoutParams as WindowManager.LayoutParams

                    val newX = (x - offsetX).toInt()
                    val newY = (y - offsetY).toInt()

                    if (newX == originalXPos && newY == originalYPos) {
                        return false
                    }

                    params.x = newX
                    params.y = newY

                    windowManager.updateViewLayout(v, params)
                }
            }
            return false
        }
    }

    private lateinit var imageView: ImageView
    private lateinit var mainView: ScrollView
    private fun gmTool() {
        if (this::mainView.isInitialized) return
        if (this::imageView.isInitialized) return
        if (isInit) return
        isInit = true
        mainView = ScrollView(activity).apply {
            visibility = View.GONE
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#5F000000"))
                addView(LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setBackgroundColor(Color.parseColor("#8F000000"))
                    addView(TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setTextColor(Color.BLUE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        text = "Tools"
                    })
                    addView(TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        setTextColor(Color.BLUE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        text = "X"
                        setOnClickListener {
                            mainView.visibility = View.GONE
                            imageView.visibility = View.VISIBLE
                        }
                    })
                })
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                    var userEdit: EditText
                    var passEdit: EditText
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(TextView(activity).apply {
                            setTextColor(Color.BLUE)
                            text = "(Check server stats)"
                            setOnClickListener {
                                Thread() {
                                    try {
                                        XposedBridge.log("$server/authentication/type")
                                        (URL("$server/authentication/type").openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            readTimeout = 8000
                                            connectTimeout = 8000
                                            val reader = BufferedReader(InputStreamReader(inputStream))
                                            if (responseCode == 200) {
                                                val response = StringBuilder()
                                                var line = ""
                                                while (reader.readLine()?.also { line = it } != null) {
                                                    response.append(line)
                                                }
                                                runOnMainThread {
                                                    text = if (response.toString() == "me.exzork.gcauth.handler.GCAuthAuthenticationHandler") "Server stats: GcAuth" else "Server stats: GcAuth not install"
                                                }
                                            } else {
                                                runOnMainThread {
                                                    text = "Server stats: Get server stats error. "
                                                }
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        runOnMainThread {
                                            text = "Server stats: Get server stats error. $e"
                                        }
                                    }
                                }.start()
                            }
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(Switch(activity).apply {
                            text = "Input"
                            setOnCheckedChangeListener { _, b ->
                                if (b) {
                                    val params = mainView.layoutParams as WindowManager.LayoutParams
                                    params.flags = params.flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
                                    windowManager.updateViewLayout(mainView, params)
                                } else {
                                    val params = mainView.layoutParams as WindowManager.LayoutParams
                                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    windowManager.updateViewLayout(mainView, params)
                                }
                            }
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(TextView(activity).apply {
                            setTextColor(Color.BLUE)
                            text = "User:"
                        })
                        addView(EditText(activity).apply {
                            userEdit = this
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            val user = sp.getString("user", "") ?: ""
                            setText(user.toCharArray(), 0, user.length)
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(TextView(activity).apply {
                            setTextColor(Color.BLUE)
                            text = "Password:"
                        })
                        addView(EditText(activity).apply {
                            passEdit = this
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            val user = sp.getString("pass", "") ?: ""
                            setText(user.toCharArray(), 0, user.length)
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(Button(activity).apply {
                            text = "Login"
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            setOnClickListener {
                                Thread() {
                                    try {
                                        (URL("$server/authentication/login").openConnection() as HttpURLConnection).apply {
                                            requestMethod = "POST"
                                            readTimeout = 8000
                                            connectTimeout = 8000
                                            doOutput = true
                                            doInput = true
                                            useCaches = false

                                            outputStream.apply {
                                                write("{\"username\":\"${userEdit.text}\",\"password\":\"${passEdit.text}\"}".toByteArray())
                                                flush()
                                            }
                                            if (responseCode == 200) {
                                                val input = inputStream
                                                val message = ByteArrayOutputStream()

                                                var len: Int
                                                val buffer = ByteArray(1024)
                                                while (input.read(buffer).also { len = it } != -1) {
                                                    message.write(buffer, 0, len)
                                                }
                                                input.close()
                                                message.close()

                                                val json = JSONObject(String(message.toByteArray()))
                                                if (json.optBoolean("success", false)) {
                                                    val token = json.optString("jwt", "")
                                                    runOnMainThread {
                                                        Toast.makeText(activity, "Login success. copy:\n${token}", Toast.LENGTH_LONG).show()
                                                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).text = token
                                                        sp.edit().run {
                                                            putString("user", userEdit.text.toString())
                                                            putString("pass", passEdit.text.toString())
                                                            apply()
                                                        }
                                                    }
                                                } else {
                                                    runOnMainThread {
                                                        Toast.makeText(activity, "Login failed, ${json.optString("message", "")}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        runOnMainThread {
                                            Toast.makeText(activity, "Login error, $e", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
                            }
                        })
                    })
                })
            })
        }

        windowManager = activity.windowManager
        windowManager.addView(mainView, WindowManager.LayoutParams(
            dp2px(activity, 200f),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        })

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        imageView = ImageView(activity).apply {
            background = moduleRes.getDrawable(R.drawable.ic_android_black_24dp).also { it.alpha = 50 }
            this.layoutParams = layoutParams
            setOnTouchListener(MoveOnTouchListener())
            setOnClickListener {
                mainView.visibility = View.VISIBLE
                it.visibility = View.GONE
            }
        }
        windowManager.addView(imageView, layoutParams)
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
        if (!forceUrl && !proxyList) return
        if (!this::server.isInitialized) return
        if (server == "") return

        if (BuildConfig.DEBUG) XposedBridge.log("old: " + method.args[args].toString())
        if (!sp.getBoolean("HookConfig", false) && method.args[args].toString().startsWith("[{\"area\":")) return
        if (proxyList) {
            for (list in proxyListRegex) {
                for (head in arrayListOf("http://", "https://")) {
                    method.args[args] = method.args[args].toString().replace(head + list, server)
                }
            }
        } else {
            val m = regex.matcher(method.args[args].toString())
            if (m.find()) {
                method.args[args] = m.replaceAll(server)
            }
        }
        if (BuildConfig.DEBUG) XposedBridge.log("new: " + method.args[args].toString())
    }
}
