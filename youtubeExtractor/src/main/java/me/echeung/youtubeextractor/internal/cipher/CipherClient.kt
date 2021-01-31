package me.echeung.youtubeextractor.internal.cipher

import android.content.Context
import android.util.Log
import com.evgenii.jsevaluator.JsEvaluator
import com.evgenii.jsevaluator.interfaces.JsCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.internal.Extractor
import me.echeung.youtubeextractor.internal.http.HttpClient
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CipherClient(
    private val http: HttpClient,
    private val contextRef: WeakReference<Context>
) {

    suspend fun decipherSignature(videoId: String, encSignatures: List<Pair<Int, String>>): String? {
        val decipherJsFileName = getCipherJS(videoId)
        decipherJsFileName ?: return null

        val decipherFunctUrl = "https://youtube.com$decipherJsFileName"
        Log.d(Extractor.TAG, "Decipher function URL: $decipherFunctUrl")

        val javascriptFile: String
        val sb = StringBuilder("")
        http.get(decipherFunctUrl) {
            sb.append(it)
            sb.append(" ")
        }
        javascriptFile = sb.toString()

        var mat = patSignatureDecFunction.matcher(javascriptFile)
        if (!mat.find()) {
            return null
        }
        val decipherFunctionName = mat.group(1)
        Log.d(Extractor.TAG, "Decipher function name: $decipherFunctionName")
        val patMainVariable = ("(var |\\s|,|;)" + decipherFunctionName.replace("$", "\\$") +
            "(=function\\((.{1,3})\\)\\{)").toPattern()
        var mainDecipherFunct: String
        mat = patMainVariable.matcher(javascriptFile)
        if (mat.find()) {
            mainDecipherFunct = "var " + decipherFunctionName + mat.group(2)
        } else {
            val patMainFunction = ("function " + decipherFunctionName.replace("$", "\\$") +
                "(\\((.{1,3})\\)\\{)").toPattern()
            mat = patMainFunction.matcher(javascriptFile)
            if (!mat.find()) {
                return null
            }
            mainDecipherFunct = "function " + decipherFunctionName + mat.group(2)
        }
        var startIndex = mat.end()
        var braces = 1
        var i = startIndex
        while (i < javascriptFile.length) {
            if (braces == 0 && startIndex + 5 < i) {
                mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";"
                break
            }
            if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
            i++
        }
        var decipherFunctions = mainDecipherFunct
        // Search the main function for extra functions and variables
        // needed for deciphering
        // Search for variables
        mat = patVariableFunction.matcher(mainDecipherFunct)
        while (mat.find()) {
            val variableDef = "var " + mat.group(2) + "={"
            if (decipherFunctions.contains(variableDef)) {
                continue
            }
            startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
            var braces = 1
            var i = startIndex
            while (i < javascriptFile.length) {
                if (braces == 0) {
                    decipherFunctions += variableDef + javascriptFile.substring(
                        startIndex,
                        i
                    ) + ";"
                    break
                }
                if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                i++
            }
        }
        // Search for functions
        mat = patFunction.matcher(mainDecipherFunct)
        while (mat.find()) {
            val functionDef = "function " + mat.group(2) + "("
            if (decipherFunctions.contains(functionDef)) {
                continue
            }
            startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
            var braces = 0
            var i = startIndex
            while (i < javascriptFile.length) {
                if (braces == 0 && startIndex + 5 < i) {
                    decipherFunctions += functionDef + javascriptFile.substring(
                        startIndex,
                        i
                    ) + ";"
                    break
                }
                if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                i++
            }
        }
        Log.d(Extractor.TAG, "Decipher function: $decipherFunctions")

        return withContext(Dispatchers.Main) {
            decipherViaWebView(encSignatures, decipherFunctions, decipherFunctionName)
        }
    }

    private fun getCipherJS(videoId: String): String? {
        val sbStreamMap = StringBuilder()
        http.get("https://youtube.com/watch?v=$videoId") { line: String ->
            sbStreamMap.append(line.replace("\\\"", "\""))
        }
        val streamMap = sbStreamMap.toString()
        var mat = patDecryptionJsFile.matcher(streamMap)
        if (!mat.find()) {
            mat = patDecryptionJsFileWithoutSlash.matcher(streamMap)
        }
        if (mat.find()) {
            return mat.group(0).replace("\\/", "/")
        }
        return null
    }

    private suspend fun decipherViaWebView(encSignatures: List<Pair<Int, String>>, decipherFunctions: String, decipherFunctionName: String): String? {
        val context = contextRef.get() ?: return null

        val script = """
            $decipherFunctions
            function decipher() {
                return ${encSignatures.map { it.second }.joinToString(" +\n") { "($decipherFunctionName(\"$it\"))"}};
            }
            decipher();
        """.trimIndent()

        return suspendCoroutine { cont ->
            JsEvaluator(context).evaluate(script, object : JsCallback {
                override fun onResult(result: String) {
                    cont.resume(result)
                }

                override fun onError(errorMessage: String) {
                    Log.e(Extractor.TAG, errorMessage)
                    cont.resume(null)
                }
            })
        }
    }

    companion object {
        private val patDecryptionJsFile by lazy {
            "\\\\/s\\\\/player\\\\/([^\"]+?)\\.js".toPattern()
        }
        private val patDecryptionJsFileWithoutSlash by lazy {
            "/s/player/([^\"]+?).js".toPattern()
        }
        private val patSignatureDecFunction by lazy {
            "(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)".toPattern()
        }

        private val patVariableFunction by lazy {
            "([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(".toPattern()
        }
        private val patFunction by lazy {
            "([{; =])([a-zA-Z$\\_][a-zA-Z0-9$]{0,2})\\(".toPattern()
        }
    }
}
