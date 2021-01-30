package me.echeung.youtubeextractor;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.echeung.youtubeextractor.internal.FormatMap;

public abstract class YouTubeExtractor extends AsyncTask<String, Void, SparseArray<YtFile>> {

    private final static boolean CACHING = true;

    private final static String LOG_TAG = "YouTubeExtractor";
    private final static String CACHE_FILE_NAME = "decipher_js_funct";

    private static final FormatMap formatMap = new FormatMap();
    private final WeakReference<Context> refContext;
    private final String cacheDirPath;

    private String videoID;
    private VideoMeta videoMeta;

    private volatile String decipheredSignature;

    private static String decipherJsFileName;
    private static String decipherFunctions;
    private static String decipherFunctionName;

    private final Lock lock = new ReentrantLock();
    private final Condition jsExecuting = lock.newCondition();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36";

    private static final Pattern patYouTubePageLink = Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)");
    private static final Pattern patYouTubeShortLink = Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)");

    private static final Pattern patTitle = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern patAuthor = Pattern.compile("\"author\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patChannelId = Pattern.compile("\"channelId\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patLength = Pattern.compile("\"lengthSeconds\"\\s*:\\s*\"(\\d+?)\"");
    private static final Pattern patViewCount = Pattern.compile("\"viewCount\"\\s*:\\s*\"(\\d+?)\"");
    private static final Pattern patShortDescript = Pattern.compile("\"shortDescription\"\\s*:\\s*\"(.+?)\"");
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
    private boolean useHttp = false;

    public YouTubeExtractor(@NonNull Context context) {
        refContext = new WeakReference<>(context);
        cacheDirPath = context.getCacheDir().getAbsolutePath();
    }

    @Override
    protected void onPostExecute(SparseArray<YtFile> ytFiles) {
        onExtractionComplete(ytFiles, videoMeta);
    }

    /**
     * Start the extraction.
     *
     * @param youtubeLink       the youtube page link or video id
     */
    public void extract(String youtubeLink) {
        this.execute(youtubeLink);
    }

    protected abstract void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta videoMeta);

    @Override
    protected SparseArray<YtFile> doInBackground(String... params) {
        videoID = null;
        String ytUrl = params[0];
        if (ytUrl == null) {
            return null;
        }
        Matcher mat = patYouTubePageLink.matcher(ytUrl);
        if (mat.find()) {
            videoID = mat.group(3);
        } else {
            mat = patYouTubeShortLink.matcher(ytUrl);
            if (mat.find()) {
                videoID = mat.group(3);
            } else if (ytUrl.matches("\\p{Graph}+?")) {
                videoID = ytUrl;
            }
        }
        if (videoID != null) {
            try {
                return getStreamUrls();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(LOG_TAG, "Wrong YouTube link format");
        }
        return null;
    }

    private SparseArray<YtFile> getStreamUrls() throws IOException, InterruptedException {
        String ytInfoUrl = (useHttp) ? "http://" : "https://";
        ytInfoUrl += "www.youtube.com/get_video_info?video_id=" + videoID + "&eurl="
                + URLEncoder.encode("https://youtube.googleapis.com/v/" + videoID, "UTF-8");

        String streamMap;
        URL getUrl = new URL(ytInfoUrl);
        Log.d(LOG_TAG, "infoUrl: " + ytInfoUrl);
        HttpURLConnection urlConnection = (HttpURLConnection) getUrl.openConnection();
        urlConnection.setRequestProperty("User-Agent", USER_AGENT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            streamMap = reader.readLine();
        } finally {
            urlConnection.disconnect();
        }
        Matcher mat;
        String curJsFileName;
        SparseArray<String> encSignatures = null;

        streamMap = URLDecoder.decode(streamMap, "UTF-8");
        streamMap = streamMap.replace("\\u0026", "&");

        parseVideoMeta(streamMap);

        if(videoMeta.isLiveStream()){
            mat = patHlsvp.matcher(streamMap);
            if(mat.find()) {
                String hlsvp = URLDecoder.decode(mat.group(1), "UTF-8");
                SparseArray<YtFile> ytFiles = new SparseArray<>();

                getUrl = new URL(hlsvp);
                urlConnection = (HttpURLConnection) getUrl.openConnection();
                urlConnection.setRequestProperty("User-Agent", USER_AGENT);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("https://") || line.startsWith("http://")) {
                            mat = patHlsItag.matcher(line);
                            if (mat.find()) {
                                int itag = Integer.parseInt(mat.group(1));
                                YtFile newFile = new YtFile(formatMap.FORMAT_MAP.get(itag), line);
                                ytFiles.put(itag, newFile);
                            }
                        }
                    }
                } finally {
                    urlConnection.disconnect();
                }

                if (ytFiles.size() == 0) {
                    Log.d(LOG_TAG, streamMap);
                    return null;
                }
                return ytFiles;
            }
            return null;
        }

        // "use_cipher_signature" disappeared, we check whether at least one ciphered signature
        // exists int the stream_map.
        boolean sigEnc = true, statusFail = false;
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
            if (CACHING
                    && (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null)) {
                readDecipherFunctFromCache();
            }
            Log.d(LOG_TAG, "Get from youtube page");

            getUrl = new URL("https://youtube.com/watch?v=" + videoID);
            urlConnection = (HttpURLConnection) getUrl.openConnection();
            urlConnection.setRequestProperty("User-Agent", USER_AGENT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                String line;
                StringBuilder sbStreamMap = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    // Log.d("line", line);
                    sbStreamMap.append(line.replace("\\\"", "\""));
                }
                streamMap = sbStreamMap.toString();
            } finally {
                urlConnection.disconnect();
            }
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

        SparseArray<YtFile> ytFiles = new SparseArray<>();

        if (sigEnc) {
            mat = patCipher.matcher(streamMap);
        }
        else {
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
            YtFile newVideo = new YtFile(format, url);
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
                    YtFile newFile = new YtFile(formatMap.FORMAT_MAP.get(key), url);
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

    private boolean decipherSignature(final SparseArray<String> encSignatures) throws IOException {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            String decipherFunctUrl = "https://youtube.com" + decipherJsFileName;

            String javascriptFile;
            URL url = new URL(decipherFunctUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("User-Agent", USER_AGENT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                StringBuilder sb = new StringBuilder("");
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append(" ");
                }
                javascriptFile = sb.toString();
            } finally {
                urlConnection.disconnect();
            }

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
                if (CACHING) {
                    writeDeciperFunctToChache();
                }
            } else {
                return false;
            }
        } else {
            decipherViaWebView(encSignatures);
        }
        return true;
    }

    private void parseVideoMeta(String getVideoInfo) {
        boolean isLiveStream = false;
        String title = null, author = null, channelId = null, shortDescript = null;
        long viewCount = 0, length = 0;
        Matcher mat = patTitle.matcher(getVideoInfo);
        if (mat.find()) {
            title = mat.group(1);
        }

        mat = patHlsvp.matcher(getVideoInfo);
        if(mat.find())
            isLiveStream = true;

        mat = patAuthor.matcher(getVideoInfo);
        if (mat.find()) {
            author = mat.group(1);
        }
        mat = patChannelId.matcher(getVideoInfo);
        if (mat.find()) {
            channelId = mat.group(1);
        }
        mat = patShortDescript.matcher(getVideoInfo);
        if (mat.find()) {
            shortDescript = mat.group(1);
        }

        mat = patLength.matcher(getVideoInfo);
        if (mat.find()) {
            length = Long.parseLong(mat.group(1));
        }
        mat = patViewCount.matcher(getVideoInfo);
        if (mat.find()) {
            viewCount = Long.parseLong(mat.group(1));
        }
        videoMeta = new VideoMeta(videoID, title, author, channelId, length, viewCount, isLiveStream, shortDescript);
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

    /**
     * Set default protocol of the returned urls to HTTP instead of HTTPS.
     * HTTP may be blocked in some regions so HTTPS is the default value.
     * <p/>
     * Note: Enciphered videos require HTTPS so they are not affected by
     * this.
     */
    public void setDefaultHttpProtocol(boolean useHttp) {
        this.useHttp = useHttp;
    }

    private void writeDeciperFunctToChache() {
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
