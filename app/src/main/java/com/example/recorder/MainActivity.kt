package com.example.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import com.example.recorder.R.id.camera
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.Facing
import com.otaliastudios.cameraview.SessionType
import com.otaliastudios.cameraview.VideoQuality
import kotlinx.android.synthetic.main.activity_main.*
import nl.bravobit.ffmpeg.FFmpeg
import top.defaults.camera.Photographer
import top.defaults.camera.PhotographerHelper
import java.io.File
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import org.jetbrains.anko.*
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.example.recorder.R.id.btnRecord


class MainActivity : AppCompatActivity() {

    var photographer: Photographer? = null

    var isRecording = false

    var ffmpeg: FFmpeg? = null

    var selectedQuality = VideoQuality.MAX_QVGA
//    var cameraListener: SimpleOnEventListener = object: SimpleOnEventListener(){
//
//        override fun onDeviceConfigured() {
//            super.onDeviceConfigured()
//        }
//
//        override fun onStartRecording() {
//            super.onStartRecording()
//            println("Recording")
//        }
//
//        override fun onFinishRecording(filePath: String?) {
//            super.onFinishRecording(filePath)
//            println("Finish")
//        }
//
//        override fun onError(error: Error?) {
//            super.onError(error)
//            println(error?.message)
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        populateSpinner()
        if (FFmpeg.getInstance(this).isSupported()) {
            println("Supported")
            ffmpeg = FFmpeg.getInstance(this@MainActivity)
        } else {
            println("Not supported")
        }

        val path = Environment.getExternalStorageDirectory().absolutePath+"/record_output"
        val f = File(path)
        if(!f.exists()){
            f.mkdir()
        }

        camera?.addCameraListener(object: CameraListener() {
            override fun onVideoTaken(video: File?) {
                super.onVideoTaken(video)
                addWatermark(video)
            }
        })

        camera?.sessionType = SessionType.VIDEO
        camera?.facing = Facing.BACK
        camera?.videoQuality = selectedQuality

        btnRecord?.setOnClickListener {
            if(isRecording){
                btnRecord.text = "Start recording"
                camera?.stopCapturingVideo()
            }else {
                val fl = File(path, System.currentTimeMillis().toString() +".mp4")
                camera?.startCapturingVideo(fl)
                btnRecord.text = "Stop recording"
            }
            isRecording = !isRecording
        }
    }

    fun populateSpinner(){
        val spinnerItems = arrayOf("QVGA", "480p", "720p", "1080p")
        val adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, spinnerItems)
        spinner?.adapter = adapter
        spinner?.setSelection(0)

        spinner?.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                when(p2){
                    0 -> selectedQuality = VideoQuality.MAX_QVGA
                    1 -> selectedQuality = VideoQuality.MAX_480P
                    2 -> selectedQuality = VideoQuality.MAX_720P
                    3 -> selectedQuality = VideoQuality.MAX_1080P
                    else -> selectedQuality = VideoQuality.MAX_QVGA
                }
                camera?.videoQuality = selectedQuality
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }
        }
    }

    fun addWatermark(video: File?){
        val pd = indeterminateProgressDialog("Please Wait")
        pd.setCancelable(false)
        pd.show()

        val startTime = System.currentTimeMillis()

        val path = Environment.getExternalStorageDirectory().absolutePath+"/record_output"

        val output = File(path, video?.name + "-processed.mp4").absolutePath
        val cmd =  arrayOf(
                "-i",
                video?.absolutePath,
                "-vf",
                "drawtext=text='WATERMARK': fontfile=/system/fonts/Roboto-Regular.ttf: fontcolor=white: x=(w-text_w)/2: y=H-60",
                "-codec:a",
                "copy",
                "-preset",
                "ultrafast",
                output)

        doAsync {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg?.execute(cmd, object : ExecuteBinaryResponseHandler() {

                override fun onStart() {
                    println("Starting")
                }

                override fun onProgress(message: String?) {
                    println("Progress: $message")

                }

                override fun onFailure(message: String?) {
                    println(message)
                }

                override fun onSuccess(message: String?) {
                    println("Succes: $message")
                }

                override fun onFinish() {
                    println("Finish")
                    val endTime = System.currentTimeMillis()

                    uiThread {
                        alert("Execution time: ${endTime - startTime} miliseconds\nOutput: $output", "Elapsed time").show()
                        pd.dismiss()
                    }
                }

            })
        }
    }

    private fun initHelper(photographer: Photographer?){
        val photographerHelper = PhotographerHelper(photographer)
        photographerHelper.setFileDir("recorder") // set directory for image/video saving
        photographerHelper.flip() // flip back/front camera
        photographerHelper.switchMode() // switch between image capture/video record
    }

    override fun onResume() {
        super.onResume()
        if(hasPermissions()){
            camera?.start()
        }else{
            requestPermission()
        }
    }

    fun hasPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.forEach {
                if(ActivityCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED){
                    return false
                }
            }
        return true
    }

    fun requestPermission(){
        ActivityCompat.requestPermissions(this@MainActivity,
                 arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == 1){
            var allPermissionGranted = true
            grantResults.forEach {
                if(it != PackageManager.PERMISSION_GRANTED){
                    allPermissionGranted = false
                }
            }

            if(allPermissionGranted){
                camera?.start()
            }else{
                Toast.makeText(this@MainActivity, "Need permissions", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onPause() {
        super.onPause()
        camera?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera?.destroy()
    }
}
