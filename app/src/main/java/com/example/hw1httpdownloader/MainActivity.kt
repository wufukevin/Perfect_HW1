package com.example.hw1httpdownloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore

import android.util.Log
import android.view.View
import android.widget.*
import java.io.InputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class downloadWebPicture{
    var current_url:String? = null
    var img: Bitmap? = null
    var progress: Int = 0
    var tempDownloadImage = ByteArray(1)
    var length: Int = 0
    var desPos = 0
    var isPause:Boolean = false
    var isOver:Boolean = false

    fun handleWebPic(url: String?, handler: Handler) {
        Log.v("kevin Debug", "new thread")
        Thread(object : Runnable {
            override fun run() {
                img = getUrlPic(url, handler)
                if (img != null){
                    val msg = Message()
                    msg.what = 1
                    handler.sendMessage(msg)
                }
            }
        }).start()

    }
//    ?? Synchronized
    @Synchronized
    fun getUrlPic(url: String?, handler: Handler): Bitmap?{
        var webImg: Bitmap? = null
        try {
            val imgUrl = URL(url)

            val httpURLConnection: HttpURLConnection = imgUrl.openConnection() as HttpURLConnection
            httpURLConnection.setRequestProperty("Range", "bytes=" + this.desPos + "-")
            httpURLConnection.connect()

            val inputStream: InputStream = httpURLConnection.inputStream

            val tmpLength = 512
            val tmp = ByteArray(tmpLength)
            var readLen = 0

            if (url != current_url){
                this.current_url = url
                this.length = httpURLConnection.contentLength
                this.tempDownloadImage = ByteArray(this.length)
            }

            Log.v("kevin Debug", "connect success")

            if (this.length != -1) {
                Log.v("kevin Debug", "start download")
                while (inputStream.read(tmp).also { readLen = it } > 0) {
                    try {
                        Thread.sleep(1)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    System.arraycopy(tmp, 0, this.tempDownloadImage, this.desPos, readLen)
                    this.desPos += readLen

                    this.progress = (this.desPos * 100 / this.length)
//                    Log.v("kevin current progress", this.progress.toString())

                    val msg = Message()
                    msg.what = 2
                    handler.sendMessage(msg)

                    if (isPause){
                        Log.v("kevin test", "download pause")
                        break
                    }
                }

                if (this.desPos == this.length){
                    Log.v("kevin Debug", "finish download and write the img file")
                    webImg = BitmapFactory.decodeByteArray(this.tempDownloadImage, 0, this.tempDownloadImage.size)
                    cleanTempData()
                }else if(isOver){
                    Log.v("kevin Debug", "delete the temp file")
                    cleanTempData()
                    val msg = Message()
                    msg.what = 3
                    handler.sendMessage(msg)
                    this.isPause = true
                }else{
                    throw Exception("Only read " + this.desPos + " bytes")
                }
            }
            httpURLConnection.disconnect()
        } catch (e: Exception) {
            Log.v("kevin IOException", e.toString())
        }
    Log.v("kevin Debug", "close thread")
    return webImg
    }

    fun cleanTempData(){
        this.current_url = null
        this.img = null
        this.progress = 0
        this.tempDownloadImage = ByteArray(1)
        this.desPos = 0
        this.isPause = false
        this.isOver = false
    }
}

class MainActivity : AppCompatActivity() {
    var url_3 = "https://media.52poke.com/wiki/9/9b/816Sobble.png"
    var url_5 = "https://images2.gamme.com.tw/news2/2017/77/08/qZqRoJ_WlKWXsKU.jpg"

    private var picDownloadManager : downloadWebPicture? = null

    // 新增 android:usesCleartextTraffic="true" under application ??
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.v("kevin Debug", "The onCreate() event")

        picDownloadManager = downloadWebPicture()
    }

    override fun onResume() {
        super.onResume()
        Log.v("kevin Debug", "The onResume() event")
        val starButton = findViewById<Button>(R.id.startButton)
        val pauseButton = findViewById<Button>(R.id.pauseButton)
        val resumeButton = findViewById<Button>(R.id.resumeButton)
        val cancelButton = findViewById<Button>(R.id.cancelButton)
        val editText = findViewById<EditText>(R.id.editText)
        val imageView = findViewById<View>(R.id.imageView) as ImageView
        imageView.setImageBitmap(null)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar) as ProgressBar
        val progressNumb = findViewById<TextView>(R.id.progressNumb)
//      Looper.getMainLooper() ??
        val pictureTitle = "new picture"
        val mHandler = object : Handler(Looper.getMainLooper()){
            override fun handleMessage(msg: Message) {
                when (msg.what){
                    1 -> {
//                            progressBar.isVisible = false
                        // use !! is not safe
                        val savedUrl = saveImage(picDownloadManager!!.img!!, pictureTitle)
                        imageView.setImageBitmap(picDownloadManager!!.img)
                    }
                    2 -> {
                        progressNumb.text = picDownloadManager!!.progress.toString()
                        progressBar.setProgress(picDownloadManager!!.progress)
                    }
                    3 -> {
                        imageView.setImageResource(0)
                        progressNumb.text = ""
                        progressBar.setProgress(0)
                    }
                }
                super.handleMessage(msg)
            }
        }
        var inputUrl = url_5

        starButton.setOnClickListener {
            Log.v("kevin Debug", "startButton")
//            progressBar.isVisible = true
            inputUrl = editText.text.toString()
            if(inputUrl == ""){
                inputUrl = url_5
            }
            imageView.setImageResource(0)
            picDownloadManager!!.cleanTempData()
            picDownloadManager!!.handleWebPic(inputUrl, mHandler)
        }

        pauseButton.setOnClickListener {
            Log.v("kevin Debug", "pauseButton")
            picDownloadManager!!.isPause = true
        }

        resumeButton.setOnClickListener {
            Log.v("kevin Debug", "resumeButton")
            picDownloadManager!!.isPause = false
            picDownloadManager!!.handleWebPic(inputUrl, mHandler)
        }

        cancelButton.setOnClickListener {
            picDownloadManager!!.isPause = true
            picDownloadManager!!.isOver = true
            picDownloadManager!!.handleWebPic(inputUrl, mHandler)
        }
    }

    private fun saveImage(image:Bitmap, title:String): Uri {
        val savedImageURL = MediaStore.Images.Media.insertImage(
            contentResolver,
            image,
            title,
            "Image of $title"
        )

        return Uri.parse(savedImageURL)
    }

}