package xfk233.genshinproxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.XModuleResources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.widget.*
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import xfk233.genshinproxy.Utils.dp2px
import xfk233.genshinproxy.Utils.isInit
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.*
import kotlin.system.exitProcess

class Hook {
    private val regex = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob)\\.com")
    private lateinit var server: String
    private var forceUrl = false
    private lateinit var modulePath: String
    private lateinit var moduleRes: XModuleResources
    private lateinit var windowManager: WindowManager
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

    private val activityList: ArrayList<Activity> = arrayListOf()
    private var activity: Activity
        get() {
            for (mActivity in activityList) {
                if (mActivity.isFinishing) {
                    activityList.remove(mActivity)
                } else {
                    return mActivity
                }
            }
            throw Throwable("Activity not found.")
        }
        set(value) {
            activityList.add(value)
        }

    private fun getDefaultSSLSocketFactory(): SSLSocketFactory {
        return SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(), arrayOf<TrustManager>(DefaultTrustManager()), SecureRandom())
        }.socketFactory
    }

    private fun getDefaultHostnameVerifier(): HostnameVerifier {
        return DefaultHostnameVerifier()
    }

    class DefaultHostnameVerifier : HostnameVerifier {
        @SuppressLint("BadHostnameVerifier")
        override fun verify(p0: String?, p1: SSLSession?): Boolean {
            return true
        }

    }

    @SuppressLint("CustomX509TrustManager")
    private class DefaultTrustManager : X509TrustManager {

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
        TrustMeAlready().initZygote()
    }

    private var startForceUrl = false
    private var startProxyList = false
    private lateinit var dialog: LinearLayout

    @SuppressLint("WrongConstant", "ClickableViewAccessibility")
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.miHoYo.GenshinImpact") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        findMethod("com.combosdk.openapi.ComboApplication") { name == "attachBaseContext" }.hookBefore {
            val context = it.args[0] as Context
            sp = context.getSharedPreferences("serverConfig", 0)
            forceUrl = sp.getBoolean("forceUrl", false)
            startForceUrl = forceUrl
            server = sp.getString("serverip", "") ?: ""
            proxyList = sp.getBoolean("ProxyList", false)
            startProxyList = proxyList
            if (sp.getBoolean("KeepSSL", false)) sslHook()
        }
        hook()
        findMethod(Activity::class.java, true) { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
        }
        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
            showDialog()
            activity.windowManager.addView(LinearLayout(activity).apply {
                dialog = this
                visibility = View.GONE
                background = ShapeDrawable().apply {
                    shape = RoundRectShape(floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null)
                    paint.color = 0xDFEFEDF5.toInt()
                }
                addView(TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                        it.gravity = Gravity.CENTER_VERTICAL
                    }
                    setPadding(10, 10, 10, 10)
                    gravity = Gravity.CENTER
                })
            }, WindowManager.LayoutParams(dp2px(activity, 200f), dp2px(activity, 150f), WindowManager.LayoutParams.TYPE_APPLICATION, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.CENTER_VERTICAL
                x = 0
                y = 0
            })
        }
    }

    private fun httpUtils(url: String, mode: String = "GET", data: String = "", callback: (HttpURLConnection, String) -> Unit) {
        var ret: String
        URL("$server$url").apply {
            val conn = if (server.startsWith("https")) {
                (openConnection() as HttpsURLConnection).apply {
                    sslSocketFactory = getDefaultSSLSocketFactory()
                    hostnameVerifier = getDefaultHostnameVerifier()
                }
            } else {
                openConnection() as HttpURLConnection
            }.apply {
                requestMethod = mode
                readTimeout = 8000
                connectTimeout = 8000
                if (mode == "POST") {
                    doOutput = true
                    doInput = true
                    useCaches = false
                    outputStream.apply {
                        write(data.toByteArray())
                        flush()
                    }
                    val input = inputStream
                    val message = ByteArrayOutputStream()

                    var len: Int
                    val buffer = ByteArray(1024)
                    while (input.read(buffer).also { len = it } != -1) {
                        message.write(buffer, 0, len)
                    }
                    input.close()
                    message.close()

                    ret = String(message.toByteArray())
                } else {
                    val response = StringBuilder()
                    var line = ""
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    while (reader.readLine()?.also { line = it } != null) {
                        response.append(line)
                    }
                    ret = response.toString()
                }
            }
            callback(conn, ret)
        }
    }

    private fun showDialog() {
        AlertDialog.Builder(activity).apply {
            setCancelable(false)
            setTitle(moduleRes.getString(R.string.SelectServer))
            setMessage(moduleRes.getString(R.string.Tips))
            setNegativeButton(moduleRes.getString(R.string.Settings)) { _, _ ->
                AlertDialog.Builder(activity).apply {
                    setMessage(moduleRes.getString(R.string.Tips2))
                    setCancelable(false)
                    setView(ScrollView(context).apply {
                        setPadding(25, 0, 25, 0)
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
                            addView(Switch(activity).apply {
                                text = moduleRes.getString(R.string.KeepSSL)
                                isChecked = sp.getBoolean("KeepSSL", false)
                                setOnCheckedChangeListener { _, b ->
                                    sp.edit().run {
                                        putBoolean("KeepSSL", b)
                                        apply()
                                    }
                                }
                            })
                        })
                    })
                    setPositiveButton(moduleRes.getString(R.string.Back)) { _, _ ->
                        showDialog()
                    }
                    setNeutralButton(moduleRes.getString(R.string.ExitGames)) { _, _ ->
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
                    Thread {
                        runOnMainThread {
                            XposedHelpers.callMethod(dialog.getChildAt(0), String(Base64.decode("c2V0VGV4dA==", Base64.DEFAULT)), String(Base64.decode(String(byteArrayOf(53, 112, 121, 115, 53, 113, 105, 104, 53, 90, 50, 88, 53, 112, 105, 118, 53, 89, 87, 78, 54, 76, 83, 53, 53, 53, 113, 69, 76, 67, 68, 108, 112, 111, 76, 109, 110, 112, 122, 107, 118, 97, 68, 109, 109, 75, 47, 111, 116, 75, 51, 107, 117, 98, 68, 110, 109, 111, 84, 109, 114, 97, 84, 109, 113, 75, 72, 108, 110, 90, 102, 109, 105, 74, 98, 111, 118, 97, 47, 107, 117, 55, 98, 106, 103, 73, 74, 99, 98, 117, 109, 67, 111, 43, 83, 53, 105, 79, 83, 57, 111, 79, 105, 105, 113, 43, 109, 113, 108, 43, 83, 54, 104, 117, 43, 56, 106, 79, 105, 118, 116, 43, 109, 65, 103, 79, 97, 115, 118, 105, 118, 108, 116, 54, 55, 111, 114, 52, 84, 118, 118, 73, 70, 99, 98, 108, 82, 111, 97, 88, 77, 103, 98, 87, 57, 107, 100, 87, 120, 108, 73, 71, 108, 122, 73, 71, 90, 121, 90, 87, 85, 115, 73, 71, 108, 109, 73, 72, 108, 118, 100, 83, 66, 119, 100, 88, 74, 106, 97, 71, 70, 122, 90, 87, 81, 103, 100, 71, 104, 112, 99, 121, 66, 116, 98, 50, 82, 49, 98, 71, 85, 103, 98, 51, 73, 103, 99, 50, 57, 109, 100, 72, 100, 104, 99, 109, 85, 117, 88, 71, 53, 85, 97, 71, 86, 117, 73, 72, 108, 118, 100, 83, 66, 111, 89, 88, 90, 108, 73, 71, 74, 108, 90, 87, 52, 103, 89, 50, 104, 108, 89, 88, 82, 108, 90, 67, 119, 103, 99, 71, 120, 108, 89, 88, 78, 108, 73, 72, 74, 108, 90, 110, 86, 117, 90, 67, 69, 61)), Base64.DEFAULT)).replace("\\n", "\n"))
                            dialog.visibility = View.VISIBLE
                        }
                        Thread.sleep(15000)
                        runOnMainThread {
                            dialog.visibility = View.GONE
                            activity.windowManager.removeView(dialog)
                        }
                    }.start()
                }
            }
            setNeutralButton(moduleRes.getString(R.string.OfficialServer)) { _, _ ->
                forceUrl = false
                server = ""
                if (startForceUrl || startProxyList) {
                    Toast.makeText(activity, moduleRes.getString(R.string.JoinServerError), Toast.LENGTH_LONG).show()
                    showDialog()
                }
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

                    val params: WindowManager.LayoutParams = v.layoutParams as WindowManager.LayoutParams

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
    @SuppressLint("SetTextI18n")
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
                background = ShapeDrawable().apply {
                    shape = RoundRectShape(floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null)
                    paint.color = 0x5FEFEDF5
                }
                addView(LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    background = ShapeDrawable().apply {
                        shape = RoundRectShape(floatArrayOf(18f, 18f, 18f, 18f, 0f, 0f, 0f, 0f), null, null)
                        paint.color = 0x8FEFEDF5.toInt()
                    }
                    addView(TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                            it.setMargins(15, 0, 0, 0)
                        }
                        setTextColor(Color.BLUE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        text = moduleRes.getString(R.string.Tools)
                    })
                    addView(TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                            it.setMargins(15, 0, 10, 0)
                        }
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
                    setPadding(20, 5, 20, 20)
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                    var userEdit: EditText
                    var passEdit: EditText
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(TextView(activity).apply {
                            setTextColor(Color.BLUE)
                            text = moduleRes.getString(R.string.CheckServerStatus)
                            setOnClickListener {
                                Thread {
                                    try {
                                        httpUtils("/authentication/type") { conn, data ->
                                            if (conn.responseCode == 200) {
                                                runOnMainThread {
                                                    text = if (data == "me.exzork.gcauth.handler.GCAuthAuthenticationHandler") "${moduleRes.getString(R.string.ServerStatus)}GCAuth" else "${moduleRes.getString(R.string.ServerStatus)}GCAuth${moduleRes.getString(R.string.NotInstall)}"
                                                }
                                            } else {
                                                runOnMainThread {
                                                    text = "${moduleRes.getString(R.string.ServerStatus)}${moduleRes.getString(R.string.GetServerStatusError)}"
                                                }
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        runOnMainThread {
                                            text = "${moduleRes.getString(R.string.ServerStatus)}${moduleRes.getString(R.string.GetServerStatusError)}$e"
                                        }
                                    }
                                }.start()
                            }
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(Switch(activity).apply {
                            setTextColor(Color.BLUE)
                            text = moduleRes.getString(R.string.InputSwitch)
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
                            text = moduleRes.getString(R.string.User)
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
                            text = moduleRes.getString(R.string.Password)
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
                            text = moduleRes.getString(R.string.Login)
                            background = ShapeDrawable().apply {
                                shape = RoundRectShape(floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null)
                                paint.color = Color.parseColor("#95EFEDF5")
                            }
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                                it.setMargins(0, 10, 0, 20)
                            }
                            setOnClickListener {
                                Thread {
                                    try {
                                        httpUtils("/authentication/login", "POST", "{\"username\":\"${userEdit.text}\",\"password\":\"${passEdit.text}\"}") { conn, data ->
                                            if (conn.responseCode == 200) {
                                                val json = JSONObject(data)
                                                if (json.optBoolean("success", false)) {
                                                    val token = json.optString("jwt", "")
                                                    runOnMainThread {
                                                        Toast.makeText(activity, "${moduleRes.getString(R.string.LoginSuccess)}\n${token}", Toast.LENGTH_LONG).show()
                                                        @Suppress("DEPRECATION")
                                                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).text = token
                                                        sp.edit().run {
                                                            putString("user", userEdit.text.toString())
                                                            putString("pass", passEdit.text.toString())
                                                            apply()
                                                        }
                                                    }
                                                } else {
                                                    runOnMainThread {
                                                        Toast.makeText(activity, "${moduleRes.getString(R.string.LoginFailed)}${json.optString("message", "")}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        runOnMainThread {
                                            Toast.makeText(activity, "${moduleRes.getString(R.string.LoginError)}\n$e", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
                            }
                        })
                    })
                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        addView(Button(activity).apply {
                            text = moduleRes.getString(R.string.OpenWebview)
                            background = ShapeDrawable().apply {
                                shape = RoundRectShape(floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null)
                                paint.color = Color.parseColor("#95EFEDF5")
                            }
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            setOnClickListener {
                                val webview = loadClass("com.miHoYo.sdk.webview.MiHoYoWebview")
                                webview.invokeStaticMethod("init", args(activity, "test_webview"), argTypes(Activity::class.java, String::class.java))
                                webview.invokeStaticMethod("show", args("test_webview"), argTypes(String::class.java))
                                webview.invokeStaticMethod("load", args("test_webview", "https://www.fkj233.cn/gourl/"), argTypes(String::class.java, String::class.java))
                            }
                        })
                    })
                })
            })
        }

        val mainParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 50
            y = 100
        }
        windowManager = activity.windowManager
        windowManager.addView(mainView.also { it.layoutParams = mainParams }, mainParams)

        val layoutParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        imageView = ImageView(activity).apply {
            @Suppress("DEPRECATION")
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

    private fun sslHook() {
        // OkHttp3 Hook
        findMethodOrNull("com.combosdk.lib.third.okhttp3.OkHttpClient\$Builder") { name == "build" }?.hookBefore {
            it.thisObject.invokeMethod("sslSocketFactory", args(getDefaultSSLSocketFactory()), argTypes(SSLSocketFactory::class.java))
            it.thisObject.invokeMethod("hostnameVerifier", args(getDefaultHostnameVerifier()), argTypes(HostnameVerifier::class.java))
        }
        findMethodOrNull("okhttp3.OkHttpClient\$Builder") { name == "build" }?.hookBefore {
            it.thisObject.invokeMethod("sslSocketFactory", args(getDefaultSSLSocketFactory(), DefaultTrustManager()), argTypes(SSLSocketFactory::class.java, X509TrustManager::class.java))
            it.thisObject.invokeMethod("hostnameVerifier", args(getDefaultHostnameVerifier()), argTypes(HostnameVerifier::class.java))
        }
        // WebView Hook
        arrayListOf(
            "android.webkit.WebViewClient",
            "cn.sharesdk.framework.g",
            "com.facebook.internal.WebDialog\$DialogWebViewClient",
            "com.geetest.sdk.dialog.views.GtWebView\$c",
            "com.miHoYo.sdk.webview.common.view.ContentWebView\$6"
        ).forEach {
            findMethodOrNull(it) { name == "onReceivedSslError" && parameterTypes[1] == SslErrorHandler::class.java }?.hookBefore { param ->
                (param.args[1] as SslErrorHandler).proceed()
            }
        }
        // Android HttpsURLConnection Hook
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "getDefaultSSLSocketFactory" }?.hookBefore {
            it.result = getDefaultSSLSocketFactory()
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setSSLSocketFactory" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setDefaultSSLSocketFactory" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setHostnameVerifier" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setDefaultHostnameVerifier" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "getDefaultHostnameVerifier" }?.hookBefore {
            it.result = getDefaultHostnameVerifier()
        }
    }

    private fun hook() {
        findMethod("com.miHoYo.sdk.webview.MiHoYoWebview") { name == "load" && parameterTypes[0] == String::class.java && parameterTypes[1] == String::class.java }.hookBefore {
            replaceUrl(it, 1)
        }
        findAllMethods("android.webkit.WebView") { name == "loadUrl" }.hookBefore {
            replaceUrl(it, 0)
        }
        findAllMethods("android.webkit.WebView") { name == "postUrl" }.hookBefore {
            replaceUrl(it, 0)
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
        if (method.args[args].toString() == "") return

        XposedBridge.log("old: " + method.args[args].toString())
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
        XposedBridge.log("new: " + method.args[args].toString())
    }
}