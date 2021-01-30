package me.echeung.youtubeextractor.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.evgenii.jsevaluator.JsEvaluator;
import com.evgenii.jsevaluator.interfaces.JsCallback;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.echeung.youtubeextractor.Format;
import me.echeung.youtubeextractor.Metadata;
import me.echeung.youtubeextractor.Video;
import me.echeung.youtubeextractor.YouTubeExtractor;
import me.echeung.youtubeextractor.internal.http.HttpClient;
import me.echeung.youtubeextractor.internal.parser.VideoIdParser;
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser;

public class Extractor {

    private final static String LOG_TAG = "YouTubeExtractor";
    private final static String CACHE_FILE_NAME = "decipher_js_funct";

    private final FormatMap formatMap = new FormatMap();
    private final HttpClient http = new HttpClient();

    private final WeakReference<Context> refContext;
    private final String cacheDirPath;

    private String videoId;

    private volatile String decipheredSignature;

    private static String decipherJsFileName;
    private static String decipherFunctions;
    private static String decipherFunctionName;


    private final Lock lock = new ReentrantLock();
    private final Condition jsExecuting = lock.newCondition();
    private static final Pattern patStatusOk = Pattern.compile("status=ok(&|,|\\z)");

    private static final Pattern patHlsvp = Pattern.compile("hlsvp=(.+?)(&|\\z)");
    private static final Pattern patHlsItag = Pattern.compile("/itag/(\\d+?)/");

    private static final Pattern patItag = Pattern.compile("itag=([0-9]+?)(&|\\z)");
    private static final Pattern patEncSig = Pattern.compile("s=(.{10,}?)(\\\\\\\\u0026|\\z)");
    private static final Pattern patUrl = Pattern.compile("\"url\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patCipher = Pattern.compile("\"signatureCipher\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patCipherUrl = Pattern.compile("url=(.+?)(\\\\\\\\u0026|\\z)");

    private static final Pattern patVariableFunction = Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(");
    private static final Pattern patFunction = Pattern.compile("([{; =])([a-zA-Z$_][a-zA-Z0-9$]{0,2})\\(");

    private static final Pattern patDecryptionJsFile = Pattern.compile("\\\\/s\\\\/player\\\\/([^\"]+?)\\.js");
    private static final Pattern patDecryptionJsFileWithoutSlash = Pattern.compile("/s/player/([^\"]+?).js");
    private static final Pattern patSignatureDecFunction = Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)");

    public Extractor(WeakReference<Context> refContext, String cacheDirPath) {
        this.refContext = refContext;
        this.cacheDirPath = cacheDirPath;
    }

    @Nullable
    public YouTubeExtractor.Result getYtFiles(String urlOrId) throws IOException, InterruptedException {
        videoId = new VideoIdParser().getVideoId(urlOrId);
        if (videoId == null) {
            Log.e(LOG_TAG, "Invalid YouTube link format: " + urlOrId);
            return null;
        }

        String streamMap = getStreamMap();
        Metadata metadata = new VideoMetadataParser().parseVideoMetadata(videoId, streamMap);
        SparseArray<Video> videos;
        if (metadata.isLiveStream()) {
            videos = getLiveStreamVideos(streamMap);
        } else {
             videos = getStreamVideos(streamMap);
        }

        return new YouTubeExtractor.Result(videos, metadata);
    }

    private SparseArray<Video> getStreamVideos(String streamMap) throws IOException, InterruptedException {
        Matcher mat;

        String curJsFileName;
        SparseArray<String> encSignatures = null;

        // "use_cipher_signature" disappeared, we check whether at least one ciphered signature
        // exists int the stream_map.
        boolean sigEnc = true;
        boolean statusFail = false;
        if(!patCipher.matcher(streamMap).find()) {
            sigEnc = false;
            if (!patStatusOk.matcher(streamMap).find()) {
                statusFail = true;
            }
        }

        // Some videos are using a ciphered signature we need to get the
        // deciphering js-file from the youtubepage.
        if (sigEnc || statusFail) {
            // Get the video directly from the youtubepage
            if (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null) {
                readDecipherFunctFromCache();
            }

            StringBuilder sbStreamMap = new StringBuilder();
            http.get("https://youtube.com/watch?v=" + videoId, (line) -> {
                sbStreamMap.append(line.replace("\\\"", "\""));
                return null;
            });
            streamMap = sbStreamMap.toString();
            encSignatures = new SparseArray<>();

            mat = patDecryptionJsFile.matcher(streamMap);
            if(!mat.find())
                mat = patDecryptionJsFileWithoutSlash.matcher(streamMap);
            if (mat.find()) {
                curJsFileName = mat.group(0).replace("\\/", "/");
                if (decipherJsFileName == null || !decipherJsFileName.equals(curJsFileName)) {
                    decipherFunctions = null;
                    decipherFunctionName = null;
                }
                decipherJsFileName = curJsFileName;
            }
        }

        SparseArray<Video> ytFiles = new SparseArray<>();

        if (sigEnc) {
            mat = patCipher.matcher(streamMap);
        } else {
            mat = patUrl.matcher(streamMap);
        }

        while (mat.find()) {
            String sig = null;
            String url;
            if (sigEnc) {
                String cipher = mat.group(1);
                Matcher mat2 = patCipherUrl.matcher(cipher);
                if (mat2.find()) {
                    url = URLDecoder.decode(mat2.group(1), "UTF-8");
                    mat2 = patEncSig.matcher(cipher);
                    if (mat2.find()) {
                        sig = URLDecoder.decode(mat2.group(1), "UTF-8");
                        // fix issue #165
                        sig = sig.replace("\\u0026", "&");
                        sig = sig.split("&")[0];
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            } else {
                url = mat.group(1);
            }

            Matcher mat2 = patItag.matcher(url);
            if (!mat2.find())
                continue;

            int itag = Integer.parseInt(mat2.group(1));

            if (formatMap.FORMAT_MAP.get(itag) == null) {
                Log.d(LOG_TAG, "Itag not in list:" + itag);
                continue;
            }

            // Unsupported
            if (url.contains("&source=yt_otf&"))
                continue;

            Log.d(LOG_TAG, "Itag found:" + itag);

            if (sig != null) {
                encSignatures.append(itag, sig);
            }

            Format format = formatMap.FORMAT_MAP.get(itag);
            Video newVideo = new Video(format, url);
            ytFiles.put(itag, newVideo);
        }

        if (encSignatures != null) {
            Log.d(LOG_TAG, "Decipher signatures: " + encSignatures.size() + ", videos: " + ytFiles.size());
            String signature;
            decipheredSignature = null;
            if (decipherSignature(encSignatures)) {
                lock.lock();
                try {
                    jsExecuting.await(7, TimeUnit.SECONDS);
                } finally {
                    lock.unlock();
                }
            }
            signature = decipheredSignature;
            if (signature == null) {
                return null;
            } else {
                String[] sigs = signature.split("\n");
                for (int i = 0; i < encSignatures.size() && i < sigs.length; i++) {
                    int key = encSignatures.keyAt(i);
                    String url = ytFiles.get(key).getUrl();
                    url +=  "&sig=" + sigs[i];
                    Video newFile = new Video(formatMap.FORMAT_MAP.get(key), url);
                    ytFiles.put(key, newFile);
                }
            }
        }

        if (ytFiles.size() == 0) {
            Log.d(LOG_TAG, streamMap);
            return null;
        }
        return ytFiles;
    }

    @Nullable
    private SparseArray<Video> getLiveStreamVideos(String streamMap) throws IOException {
        Matcher mat = patHlsvp.matcher(streamMap);
        if (mat.find()) {
            String hlsvp = URLDecoder.decode(mat.group(1), "UTF-8");
            SparseArray<Video> ytFiles = new SparseArray<>();

            http.get(hlsvp, (line) -> {
                if (line.startsWith("https://") || line.startsWith("http://")) {
                    Matcher matcher = patHlsItag.matcher(line);
                    if (matcher.find()) {
                        int itag = Integer.parseInt(matcher.group(1));
                        Video newFile = new Video(formatMap.FORMAT_MAP.get(itag), line);
                        ytFiles.put(itag, newFile);
                    }
                }
                return null;
            });

            if (ytFiles.size() == 0) {
                Log.d(LOG_TAG, streamMap);
                return null;
            }
            return ytFiles;
        }
        return null;
    }

    private String getStreamMap() throws IOException {
        String ytInfoUrl = "https://www.youtube.com/get_video_info?video_id=" + videoId + "&eurl="
                + URLEncoder.encode("https://youtube.googleapis.com/v/" + videoId, "UTF-8");

        AtomicReference<String> streamMap = new AtomicReference<>("");
        http.get(ytInfoUrl, (line) -> {
            streamMap.set(line);
            return null;
        });
        streamMap.set(URLDecoder.decode(streamMap.get(), "UTF-8"));
        return streamMap.get().replace("\\u0026", "&");
    }

    private boolean decipherSignature(final SparseArray<String> encSignatures) throws IOException {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            String decipherFunctUrl = "https://youtube.com" + decipherJsFileName;
            String javascriptFile;

            StringBuilder sb = new StringBuilder("");
            http.get(decipherFunctUrl, (line) -> {
                sb.append(line);
                sb.append(" ");
               return null;
            });
            javascriptFile = sb.toString();

            Log.d(LOG_TAG, "Decipher FunctURL: " + decipherFunctUrl);
            Matcher mat = patSignatureDecFunction.matcher(javascriptFile);
            if (mat.find()) {
                decipherFunctionName = mat.group(1);
                Log.d(LOG_TAG, "Decipher Functname: " + decipherFunctionName);

                Pattern patMainVariable = Pattern.compile("(var |\\s|,|;)" + decipherFunctionName.replace("$", "\\$") +
                        "(=function\\((.{1,3})\\)\\{)");

                String mainDecipherFunct;

                mat = patMainVariable.matcher(javascriptFile);
                if (mat.find()) {
                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2);
                } else {
                    Pattern patMainFunction = Pattern.compile("function " + decipherFunctionName.replace("$", "\\$") +
                            "(\\((.{1,3})\\)\\{)");
                    mat = patMainFunction.matcher(javascriptFile);
                    if (!mat.find())
                        return false;
                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2);
                }

                int startIndex = mat.end();

                for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";";
                        break;
                    }
                    if (javascriptFile.charAt(i) == '{')
                        braces++;
                    else if (javascriptFile.charAt(i) == '}')
                        braces--;
                }
                decipherFunctions = mainDecipherFunct;
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    String variableDef = "var " + mat.group(2) + "={";
                    if (decipherFunctions.contains(variableDef)) {
                        continue;
                    }
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length();
                    for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0) {
                            decipherFunctions += variableDef + javascriptFile.substring(startIndex, i) + ";";
                            break;
                        }
                        if (javascriptFile.charAt(i) == '{')
                            braces++;
                        else if (javascriptFile.charAt(i) == '}')
                            braces--;
                    }
                }
                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    String functionDef = "function " + mat.group(2) + "(";
                    if (decipherFunctions.contains(functionDef)) {
                        continue;
                    }
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length();
                    for (int braces = 0, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions += functionDef + javascriptFile.substring(startIndex, i) + ";";
                            break;
                        }
                        if (javascriptFile.charAt(i) == '{')
                            braces++;
                        else if (javascriptFile.charAt(i) == '}')
                            braces--;
                    }
                }

                Log.d(LOG_TAG, "Decipher Function: " + decipherFunctions);
                decipherViaWebView(encSignatures);
                writeDeciperFunctToCache();
            } else {
                return false;
            }
        } else {
            decipherViaWebView(encSignatures);
        }
        return true;
    }

    private void readDecipherFunctFromCache() {
        File cacheFile = new File(cacheDirPath + "/" + CACHE_FILE_NAME);
        // The cached functions are valid for 2 weeks
        if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) < 1209600000) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
                decipherJsFileName = reader.readLine();
                decipherFunctionName = reader.readLine();
                decipherFunctions = reader.readLine();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeDeciperFunctToCache() {
        File cacheFile = new File(cacheDirPath + "/" + CACHE_FILE_NAME);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8))) {
            writer.write(decipherJsFileName + "\n");
            writer.write(decipherFunctionName + "\n");
            writer.write(decipherFunctions);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void decipherViaWebView(final SparseArray<String> encSignatures) {
        final Context context = refContext.get();
        if (context == null) {
            return;
        }

        final StringBuilder stb = new StringBuilder(decipherFunctions + " function decipher(");
        stb.append("){return ");
        for (int i = 0; i < encSignatures.size(); i++) {
            int key = encSignatures.keyAt(i);
            if (i < encSignatures.size() - 1)
                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).
                        append("')+\"\\n\"+");
            else
                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).
                        append("')");
        }
        stb.append("};decipher();");

        new Handler(Looper.getMainLooper()).post(() -> new JsEvaluator(context).evaluate(stb.toString(), new JsCallback() {
            @Override
            public void onResult(String result) {
                lock.lock();
                try {
                    decipheredSignature = result;
                    jsExecuting.signal();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void onError(String errorMessage) {
                lock.lock();
                try {
                    Log.e(LOG_TAG, errorMessage);
                    jsExecuting.signal();
                } finally {
                    lock.unlock();
                }
            }
        }));
    }
}
