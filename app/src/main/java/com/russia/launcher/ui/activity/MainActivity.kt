package com.russia.launcher.ui.activity

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.russia.game.R
import com.russia.game.core.Samp
import com.russia.launcher.async.dto.response.FileInfo
import com.russia.launcher.async.task.CacheChecker
import com.russia.launcher.config.Config.DONATE_URL
import com.russia.launcher.config.Config.FORUM_URL
import com.russia.launcher.domain.enums.DownloadType
import com.russia.launcher.domain.enums.StorageElements
import com.russia.launcher.service.impl.ActivityServiceImpl
import com.russia.launcher.storage.NativeStorage
import com.russia.launcher.storage.Storage
import com.russia.launcher.ui.dialogs.EnterLockedServerPasswordDialog
import com.russia.launcher.ui.fragment.MonitoringFragment
import com.russia.launcher.ui.fragment.SettingsFragment
import com.russia.launcher.utils.MainUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private var animation: Animation? = null
    private var donateButton: LinearLayout? = null
    private var donateImage: ImageView? = null
    private var donateTV: TextView? = null
    private var monitoringButton: LinearLayout? = null
    private var monitoringFragment: MonitoringFragment? = null
    private var monitoringImage: ImageView? = null
    private var monitoringTV: TextView? = null
    private var playButton: LinearLayout? = null
    private var playImage: ImageView? = null
    private var rouletteButton: LinearLayout? = null
    private var rouletteImage: ImageView? = null
    private var rouletteTV: TextView? = null
    private var settingsButton: LinearLayout? = null
    private var settingsFragment: SettingsFragment? = null
    private var settingsImage: ImageView? = null
    private var settingsTV: TextView? = null
    private var container_layout: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTheme(R.style.AppBaseTheme)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        container_layout = findViewById(R.id.container)
        animation = AnimationUtils.loadAnimation(this, R.anim.button_click)

        monitoringTV = findViewById(R.id.monitoringTV)
        settingsTV = findViewById(R.id.settingsTV)
        rouletteTV = findViewById(R.id.forumTV)
        donateTV = findViewById(R.id.donateTV)

        monitoringImage = findViewById(R.id.monitoringImage)
        settingsImage = findViewById(R.id.settingsImage)
        rouletteImage = findViewById(R.id.forumImage)
        donateImage = findViewById(R.id.donateImage)
        playImage = findViewById(R.id.playImage)

        monitoringButton = findViewById(R.id.monitoringButton)
        settingsButton = findViewById(R.id.settingsButton)
        rouletteButton = findViewById(R.id.rouletteButton)
        donateButton = findViewById(R.id.donateButton)
        playButton = findViewById(R.id.playButton)

        monitoringFragment = MonitoringFragment()
        settingsFragment = SettingsFragment()

        if (
            savedInstanceState != null &&
            savedInstanceState.getBoolean(IS_AFTER_LOADING_KEY)
        ) {
            replaceFragment(settingsFragment)
        } else if (
            savedInstanceState == null &&
            intent.extras != null &&
            intent.extras!!.getBoolean(IS_AFTER_LOADING_KEY)
        ) {
            onClickSettings()
        } else {
            replaceFragment(monitoringFragment)
        }

        monitoringButton!!.setOnClickListener {
            onClickMonitoring()
        }

        settingsButton!!.setOnClickListener {
            onClickSettings()
        }

        rouletteButton!!.setOnClickListener {
            val address = Uri.parse(FORUM_URL)
            val openlink = Intent(Intent.ACTION_VIEW, address)
            startActivity(openlink)
        }

        donateButton!!.setOnClickListener {
            onClickDonate()
        }

        playButton!!.setOnClickListener {
            onClickPlay()
        }
    }

    private fun onClickPlay() {
        startGame()
    }

    private val isCheckSkipping: Boolean
        get() {
            val isTestMode = NativeStorage.getClientProperty("test", this)
            return TEST_MODE_ON_VALUE == isTestMode
        }

    private fun doAfterCacheChecked(fileToReload: MutableList<FileInfo>) {
        for (file in fileToReload) {
            println("invalid file = ${file.path}")
        }

        if (fileToReload.isEmpty()) {
            startGame()
        } else {
            MainUtils.FILES_TO_RELOAD = fileToReload
            MainUtils.type = DownloadType.RELOAD_GAME_FILES
            startActivity(Intent(this, LoaderActivity::class.java))
        }
    }

    /*
     * NAVARA DEVELOPER MODE:
     * الضغط على PLAY يشغّل عميل اللعبة مباشرةً.
     * لا نطلب من المطوّر اختيار ZIP في كل مرة.
     */
    private fun startGame() {
        launchGameAfterDataReady()
    }

    /*
     * تُركت دوال تجهيز البيانات القديمة موجودة حتى لا نحذف
     * وظائف المشروع الحالية. لم تعد شرطاً يمنع الضغط على PLAY.
     */
    private fun isPrivateGameDataReady(): Boolean {
        val marker = File(filesDir, PRIVATE_DATA_MARKER)
        val americanGxt = File(filesDir, "TEXT/AMERICAN.GXT")
        val mobileTxt = File(filesDir, "texdb/mobile/mobile.txt")

        return marker.exists() &&
            americanGxt.isFile &&
            mobileTxt.isFile
    }

    private fun selectGameDataZip() {
        Toast.makeText(
            this,
            "اختيار ملف اللعبة",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }

        try {
            startActivityForResult(intent, REQUEST_GAME_DATA_ZIP)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(fallback, REQUEST_GAME_DATA_ZIP)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode != REQUEST_GAME_DATA_ZIP ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri = data?.data ?: run {
            ActivityServiceImpl.showErrorMessage(
                "ملف ZIP غير صالح.",
                this
            )
            return
        }

        try {
            val takeFlags = data.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // الصلاحية المؤقتة قد تكفي لهذه العملية.
        }

        installGameDataZip(uri)
    }

    private fun installGameDataZip(uri: Uri) {
        val progressDialog =
            findViewById<ConstraintLayout>(R.id.progressDialog)

        progressDialog?.visibility = View.VISIBLE
        playButton?.isEnabled = false

        Toast.makeText(
            this,
            "جارٍ تجهيز ملفات اللعبة، لا تغلق التطبيق.",
            Toast.LENGTH_LONG
        ).show()

        Thread {
            val traceFile =
                File(getExternalFilesDir(null), "data_install_trace.txt")

            fun trace(message: String) {
                try {
                    traceFile.appendText(message + "\n")
                } catch (_: Exception) {
                }
            }

            try {
                File(filesDir, PRIVATE_DATA_MARKER).delete()

                val destinationRoot = filesDir.canonicalFile
                val destinationPrefix =
                    destinationRoot.path + File.separator

                var rootPrefix: String? = null
                var filesInstalled = 0
                var bytesInstalled = 0L

                trace("BEGIN uri=$uri")
                trace("DEST=${destinationRoot.absolutePath}")

                val rawInput = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(
                        "تعذر فتح ملف ZIP المختار."
                    )

                BufferedInputStream(rawInput, 1024 * 1024).use { bufferedInput ->
                    ZipInputStream(bufferedInput).use { zip ->
                        val buffer = ByteArray(1024 * 1024)

                        while (true) {
                            val entry = zip.nextEntry ?: break

                            var entryName =
                                entry.name.replace('\\', '/')

                            while (entryName.startsWith("/")) {
                                entryName = entryName.substring(1)
                            }

                            if (entryName.isBlank()) {
                                zip.closeEntry()
                                continue
                            }

                            if (rootPrefix == null) {
                                val firstSlash = entryName.indexOf('/')
                                rootPrefix = if (firstSlash >= 0) {
                                    entryName.substring(0, firstSlash + 1)
                                } else {
                                    ""
                                }

                                trace("ROOT_PREFIX=$rootPrefix")
                            }

                            val prefix = rootPrefix ?: ""
                            val relativeName =
                                if (
                                    prefix.isNotEmpty() &&
                                    entryName.startsWith(prefix)
                                ) {
                                    entryName.substring(prefix.length)
                                } else {
                                    entryName
                                }

                            if (relativeName.isBlank()) {
                                zip.closeEntry()
                                continue
                            }

                            val output =
                                File(destinationRoot, relativeName).canonicalFile

                            if (!output.path.startsWith(destinationPrefix)) {
                                throw SecurityException(
                                    "مسار ZIP غير آمن: $entryName"
                                )
                            }

                            if (entry.isDirectory) {
                                if (!output.exists() && !output.mkdirs()) {
                                    throw IllegalStateException(
                                        "تعذر إنشاء ${output.absolutePath}"
                                    )
                                }
                            } else {
                                output.parentFile?.let { parent ->
                                    if (!parent.exists() && !parent.mkdirs()) {
                                        throw IllegalStateException(
                                            "تعذر إنشاء ${parent.absolutePath}"
                                        )
                                    }
                                }

                                BufferedOutputStream(
                                    output.outputStream(),
                                    1024 * 1024
                                ).use { out ->
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read <= 0) break

                                        out.write(buffer, 0, read)
                                        bytesInstalled += read
                                    }

                                    out.flush()
                                }

                                filesInstalled++

                                if (filesInstalled % 50 == 0) {
                                    trace(
                                        "PROGRESS files=$filesInstalled " +
                                            "bytes=$bytesInstalled last=$relativeName"
                                    )
                                }
                            }

                            zip.closeEntry()
                        }
                    }
                }

                val americanGxt =
                    File(filesDir, "TEXT/AMERICAN.GXT")
                val mobileTxt =
                    File(filesDir, "texdb/mobile/mobile.txt")

                if (!americanGxt.isFile) {
                    throw IllegalStateException(
                        "ملف TEXT/AMERICAN.GXT غير موجود."
                    )
                }

                if (!mobileTxt.isFile) {
                    throw IllegalStateException(
                        "ملف texdb/mobile/mobile.txt غير موجود."
                    )
                }

                File(filesDir, PRIVATE_DATA_MARKER).writeText(
                    "ok\nfiles=$filesInstalled\nbytes=$bytesInstalled\n"
                )

                trace("OK files=$filesInstalled bytes=$bytesInstalled")
                trace("AMERICAN=${americanGxt.length()}")
                trace("MOBILE_TXT=${mobileTxt.length()}")
                trace("END")

                runOnUiThread {
                    progressDialog?.visibility = View.GONE
                    playButton?.isEnabled = true

                    Toast.makeText(
                        this,
                        "تم تجهيز ملفات اللعبة.",
                        Toast.LENGTH_LONG
                    ).show()

                    launchGameAfterDataReady()
                }
            } catch (e: Exception) {
                trace(
                    "FAIL type=${e.javaClass.simpleName} " +
                        "message=${e.message}"
                )

                runOnUiThread {
                    progressDialog?.visibility = View.GONE
                    playButton?.isEnabled = true

                    ActivityServiceImpl.showErrorMessage(
                        "فشل تجهيز الملفات: ${e.message}",
                        this
                    )
                }
            }
        }.start()
    }

    private fun ensureAdjustableConfig() {
        val target = File(filesDir, "Adjustable.cfg")

        if (target.isFile && target.length() > 0L) {
            return
        }

        val candidates = listOf(
            File(filesDir, "data/360Default1280x720.cfg"),
            File(filesDir, "data/360Default960x720.cfg")
        )

        val source =
            candidates.firstOrNull { it.isFile && it.length() > 0L }
                ?: return

        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } catch (_: Exception) {
        }
    }

    /*
     * يجهّز إعدادات العميل ثم يفتح شاشة اللعبة مباشرةً.
     */
    private fun launchGameAfterDataReady() {
        ensureAdjustableConfig()

        val log = File(
            getExternalFilesDir(null).toString() + "/log.txt"
        )
        log.delete()

        val cinfo = File(
            getExternalFilesDir(null).toString() + "/CINFO.BIN"
        )
        cinfo.delete()

        val minfo = File(
            getExternalFilesDir(null).toString() + "/models/MINFO.BIN"
        )
        minfo.delete()

        val nickname =
            NativeStorage.getClientProperty("name", this)

        if (StringUtils.isBlank(nickname)) {
            ActivityServiceImpl.showErrorMessage(
                "أدخل اسم اللاعب من الإعدادات أولاً.",
                this
            )
            onClickSettings()
            return
        }

        /*
         * إعدادات الاتصال الحالية للمشروع.
         * هذه القيم لا تنشئ سيرفراً جديداً؛ السيرفر يجب أن يكون متاحاً.
         */
        NativeStorage.addClientProperty("name", nickname, this)
        NativeStorage.addClientProperty("server", "1", this)
        NativeStorage.addClientProperty("ip", SERVER_IP, this)
        NativeStorage.addClientProperty("port", SERVER_PORT, this)
        NativeStorage.addClientProperty(
            "password",
            StringUtils.EMPTY,
            this
        )

        val intent = Intent(this, Samp::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP

        startActivity(intent)
        finish()
    }

    private fun saveServerPassword(password: String) {
        NativeStorage.addClientProperty("password", password, this)
        startActivity(Intent(this, Samp::class.java))
        finish()
    }

    private fun onClickSettings() {
        setTextColor(settingsButton, settingsTV, settingsImage)
        replaceFragment(settingsFragment)
    }

    private fun onClickDonate() {
        val address = Uri.parse(DONATE_URL)
        val openlink = Intent(Intent.ACTION_VIEW, address)
        startActivity(openlink)
    }

    private fun onClickMonitoring() {
        setTextColor(monitoringButton, monitoringTV, monitoringImage)
        replaceFragment(monitoringFragment)
    }

    fun setTextColor(
        linearLayout: LinearLayout?,
        textView: TextView?,
        imageView: ImageView?
    ) {
        monitoringButton!!.alpha = 0.45f
        settingsButton!!.alpha = 0.45f
        rouletteButton!!.alpha = 0.45f
        donateButton!!.alpha = 0.45f

        monitoringTV!!.setTextColor(
            resources.getColor(R.color.menuTextDisable)
        )
        settingsTV!!.setTextColor(
            resources.getColor(R.color.menuTextDisable)
        )
        rouletteTV!!.setTextColor(
            resources.getColor(R.color.menuTextDisable)
        )
        donateTV!!.setTextColor(
            resources.getColor(R.color.menuTextDisable)
        )

        monitoringImage!!.setColorFilter(
            resources.getColor(R.color.menuTextDisable),
            PorterDuff.Mode.SRC_IN
        )
        settingsImage!!.setColorFilter(
            resources.getColor(R.color.menuTextDisable),
            PorterDuff.Mode.SRC_IN
        )
        rouletteImage!!.setColorFilter(
            resources.getColor(R.color.menuTextDisable),
            PorterDuff.Mode.SRC_IN
        )
        donateImage!!.setColorFilter(
            resources.getColor(R.color.menuTextDisable),
            PorterDuff.Mode.SRC_IN
        )

        linearLayout!!.alpha = 1.0f
        textView!!.setTextColor(
            resources.getColor(R.color.menuTextEnable)
        )
        imageView!!.setColorFilter(
            resources.getColor(R.color.menuTextEnable),
            PorterDuff.Mode.SRC_IN
        )
    }

    private fun replaceFragment(fragment: Fragment?) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment!!)
            .commitAllowingStateLoss()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    public override fun onRestart() {
        super.onRestart()
    }

    public override fun onStop() {
        super.onStop()
    }

    companion object {
        private const val IS_AFTER_LOADING_KEY = "isAfterLoading"
        private const val GAME_DIRECTORY_EMPTY_SIZE = 0
        private const val SERVER_LOCKED_VALUE = 1
        private const val TEST_MODE_ON_VALUE = "1"
        private const val REQUEST_GAME_DATA_ZIP = 5206
        private const val PRIVATE_DATA_MARKER = ".betatester_data_ready"

        private const val SERVER_IP = "149.56.41.51"
        private const val SERVER_PORT = "7774"
    }
}
