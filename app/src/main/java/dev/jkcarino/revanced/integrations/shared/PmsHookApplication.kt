package dev.jkcarino.revanced.integrations.shared

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A custom [Application] class that hooks into the Android's [PackageManager] to modify the
 * package information returned for the app. This is used to override the package signatures,
 * allowing us to bypass signature verification checks.
 *
 * Source: https://github.com/L-JINBIN/ApkSignatureKiller/blob/master/hook/cc/binmt/signature/PmsHookApplication.java
 */
internal class PmsHookApplication(
    private val signature: String
) {
    /**
     * This hooks into the [PackageManager] and replaces the original package manager with a
     * proxy object that modifies the package information returned for the app.
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun hook(context: Context) {
        try {
            val byteArray = Base64.decode(signature, Base64.DEFAULT)
            val signatures = ByteArrayInputStream(byteArray).use { inputStream ->
                DataInputStream(inputStream).use { dataInputStream ->
                    val signatureCount = dataInputStream.read() and 0xFF
                    Array(signatureCount) {
                        ByteArray(dataInputStream.readInt()).also {
                            dataInputStream.readFully(it)
                        }
                    }
                }
            }

            // Get the global ActivityThread object
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod =
                activityThreadClass.getDeclaredMethod("currentActivityThread")
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            // Get the original sPackageManager from ActivityThread
            val sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager")
            sPackageManagerField.isAccessible = true
            val sPackageManager = sPackageManagerField.get(currentActivityThread)

            // Prepare a proxy object to replace the original one
            val iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager")
            val proxy = Proxy.newProxyInstance(
                iPackageManagerInterface.classLoader,
                arrayOf<Class<*>>(iPackageManagerInterface),
                PmsHookInvocationHandler(
                    packageManager = sPackageManager,
                    signatures = signatures,
                    appPackageName = context.packageName
                )
            )

            // Replace the sPackageManager field in ActivityThread
            sPackageManagerField.set(currentActivityThread, proxy)

            // Replace mPM object in ApplicationPackageManager
            val pm = context.packageManager
            val mPmField = pm.javaClass.getDeclaredField("mPM")
            mPmField.isAccessible = true
            mPmField.set(pm, proxy)

            println("PmsHook success.")
        } catch (error: Exception) {
            System.err.println("PmsHook failed.")
            error.printStackTrace()
        }
    }
}

/**
 * This class is used as part of the package manager hooking mechanism implemented in the
 * [PmsHookApplication] class.
 */
private class PmsHookInvocationHandler(
    private val packageManager: Any?,
    private val signatures: Array<ByteArray>,
    private val appPackageName: String
) : InvocationHandler {

    /**
     * This intercepts calls to the [getPackageInfo] method of the package manager and
     * modifies the returned [PackageInfo] object to use the new package signatures.
     */
    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        if ("getPackageInfo" == method.name) {
            val packageName = args!![0].toString()
            val flag = args[1].toString().toInt()
            if (flag and GET_SIGNATURES != 0 && appPackageName == packageName) {
                val packageInfo = method.invoke(packageManager, *args) as PackageInfo
                packageInfo.signatures = Array(signatures.size) { Signature(signatures[it]) }
                return packageInfo
            }
        }
        return method.invoke(packageManager, *args.orEmpty())
    }

    companion object {
        private const val GET_SIGNATURES = 0x00000040
    }
}
