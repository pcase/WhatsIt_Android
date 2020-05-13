package com.azurehorsecreations.whatsit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.gson.Gson
import com.ibm.watson.developer_cloud.android.library.camera.CameraHelper
import com.ibm.watson.developer_cloud.android.library.camera.GalleryHelper
import com.ibm.watson.developer_cloud.service.security.IamOptions
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImages
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.*

/**
 * Main activity for Whatsit
 */
class MainActivity : AppCompatActivity(), LifecycleObserver {

    private var photoFile: File? = null
    private var rightCount = 0
    private var wrongCount = 0
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val PERMISSIONS_CODE = 4
        private const val TEMP_FILENAME = "tempFile.jpg"
        private const val PHOTO_FILENAME = "photo.jpg"
    }

    /* Application level lifecycle event */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnteredForeground() {
    }

    /* Application level lifecycle event */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnteredBackground() {
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_replay -> {
                val i = Intent(
                    this,
                    MainActivity::class.java
                )
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                finish()
                startActivity(i)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar3)
        progressBar.visibility = View.INVISIBLE

        if (!isConnected()) {
            showNoNetworkDialog()
        } else {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@MainActivity);
            checkPermissions()
        }
    }

    override fun onRestart() {
        super.onRestart()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        var bitMap: Bitmap? = null

        /* Camera image */
        if (requestCode == CameraHelper.REQUEST_IMAGE_CAPTURE) {
            if (data != null && data.extras != null) {
                bitMap = data.extras.get("data") as Bitmap
            }

            /* Gallery image */
        } else if (requestCode == GalleryHelper.PICK_IMAGE_REQUEST) {
            if (data != null) {
                val contentURI = data.data
                bitMap = MediaStore.Images.Media.getBitmap(
                    applicationContext.contentResolver,
                    contentURI
                )
            } else {
                Toast.makeText(this@MainActivity, "No photos were found or selected", Toast.LENGTH_SHORT).show()
                showPickImageSourceDialog()
            }
        }

        /* Classify image */
        if (bitMap != null) {
            classifyImages(bitMap)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                          permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_CODE -> {
                // When request is cancelled, the results array are empty
                if (grantResults.isNotEmpty() &&
                    ((grantResults[0]
                            + grantResults[1])
                            == PackageManager.PERMISSION_GRANTED)) {
                    /* Permissions are granted */
                    showStartDialog()
                } else {
                    /* Permissions are denied */
                }
                return
            }
        }
    }

    /**
     * Calls VisualRecognition service to classify image
     */
    private fun classifyImages(bitMap: Bitmap){
        var guess: String

            val options: IamOptions = IamOptions.Builder()
                .apiKey(getString(R.string.api_key))
                .build()

            val service =
                VisualRecognition(
                    getString(R.string.version_date),
                    options
                )

            try {
                /* Create images stream file */
                val mFile1 = Environment.getExternalStorageDirectory()
                val fileName = TEMP_FILENAME
                val mFile2 = File(mFile1, fileName)
                val outStream: FileOutputStream
                outStream = FileOutputStream(mFile2)
                bitMap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
                outStream.close()
                val sdPath: String =
                    mFile1.absolutePath.toString() + "/" + fileName
                val imagesStream: InputStream = FileInputStream(sdPath)

                /* Set up classify options */
                val classifyOptions =
                    ClassifyOptions.Builder()
                        .imagesFile(imagesStream)
                        .imagesFilename(fileName)
                        .threshold(0.6.toFloat())
                        .classifierIds(listOf("default"))
                        .build()

                var result: ClassifiedImages?

                /* Classify the image */

                progressBar.visibility = View.VISIBLE

                GlobalScope.launch(Dispatchers.IO) {
                   result = service.classify(classifyOptions).execute()

                    progressBar.visibility = View.INVISIBLE

                    /* Parse the results */
                    val gson = Gson()
                    val json: String = gson.toJson(result)
                    Log.d("json", json)

                    val jsonObject = JSONObject(json)
                    val jsonArray: JSONArray = jsonObject.getJSONArray("images")
                    val jsonObject1: JSONObject = jsonArray.getJSONObject(0)
                    val jsonArray1: JSONArray = jsonObject1.getJSONArray("classifiers")
                    val jsonObject2: JSONObject = jsonArray1.getJSONObject(0)
                    val jsonArray2: JSONArray = jsonObject2.getJSONArray("classes")
                    val jsonObject3: JSONObject = jsonArray2.getJSONObject(0)

                    /* Get the top guess */
                    guess = jsonObject3.getString("class")

                    /* Display the guess in a dialog */
                    runOnUiThread() {
                        showGuessDialog(guess)
                    }
                }

            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
    }

    /**
     * Checks and requests camera permission then calls takePicture()
     */
    private fun checkPermissions() {

        /* Check camera and external storage permissions */
        if (ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.CAMERA) +
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            /* Permission is not granted */
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,
                    Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                /* Show an alert dialog here with request explanation */
                val builder =
                    AlertDialog.Builder(this@MainActivity)
                builder.setMessage(
                    "Camera and Write External" +
                            " Storage permissions are required to do the task."
                )
                builder.setTitle("Please grant those permissions")
                builder.setPositiveButton("OK"
                ) { _, i ->
                    ActivityCompat.requestPermissions(
                        this@MainActivity, arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        PERMISSIONS_CODE
                    )
                }
                builder.setNeutralButton("Cancel", null)
                val dialog = builder.create()
                dialog.show()
            } else {
                /* Request the permission directly */
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSIONS_CODE)
            }
        } else {
            /* Permission has already been granted */
            showStartDialog()
        }
    }

    /**
     * Displays start of game dialog
     */
    private fun showStartDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Whatizit")
        builder.setMessage("I'm an artificial intelligence being. Want to see how smart I am?. You show me a picture, and I'll guess what it is.\n\nWould you like to play?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            dialog.dismiss()
            showPickImageSourceDialog()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    /**
     * Displays play again dialog
     */
    private fun showResultDialog() {
        val randomNumber = (0..9).random()
        var comment: String
        comment = if (rightCount > wrongCount) {
            Constants.WE_WIN[randomNumber]
        } else {
            Constants.I_GUESSED_WRONG[randomNumber]
        }
        val resultMessage = "Right guesses: $rightCount\nWrong guesses: $wrongCount\n\n$comment\n\n"
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Results")
        builder.setMessage("$resultMessage Do you want to play again?")
        builder.setNegativeButton("No") { dialog, which ->
            dialog.dismiss()
            Toast.makeText(this@MainActivity,"Thanks for playing!",Toast.LENGTH_SHORT).show()
        }
        builder.setPositiveButton("Yes") { dialog, _ ->
            dialog.dismiss()
            showPickImageSourceDialog()
        }
        builder.show()
    }

    /**
     * Displays guess dialog
     */
    private fun showGuessDialog(guess: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Whatizit")
        builder.setMessage("Is it a $guess ?")
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            this.wrongCount += 1
            showResultDialog()
        }
        builder.setPositiveButton("Yes") { dialog, _ ->
            dialog.dismiss()
            this.rightCount += 1
            showResultDialog()
        }
        builder.show()
    }

    /**
     * Displays dialog to choose between camera and gallery for image classification
     */
    private fun showPickImageSourceDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Whatizit")
        builder.setMessage("Camera or photo library?")
        builder.setNegativeButton("Photo Library") { dialog, _ ->
            dialog.dismiss()
            pickPhotoFromGallery()
        }
        builder.setPositiveButton("Camera") { dialog, _ ->
            dialog.dismiss()
            takePicture()
        }
        builder.show()
    }

    /**
     *   Returns the File for a photo stored on disk given the fileName
     */
    private fun getPhotoFileUri(fileName: String): File? {
        /* Get safe storage directory for photos */
        val mediaStorageDir =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), getString(R.string.app_tag))

        /* Create the storage directory if it does not exist */
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d(getString(R.string.app_tag), "failed to create directory")
        }

        /* Return the file target for the photo based on filename */
        return File(mediaStorageDir.path + File.separator + fileName)
    }

    /**
     * Takes a picture with the camera
     */
    private fun takePicture() {
        val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = getPhotoFileUri(PHOTO_FILENAME)
        val fileProvider: Uri =
            FileProvider.getUriForFile(this@MainActivity, BuildConfig.APPLICATION_ID + ".provider", photoFile!!)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePicture, CameraHelper.REQUEST_IMAGE_CAPTURE)
        }
    }

    /**
     * Selects an image from the gallery
     */
    private fun pickPhotoFromGallery() {
        val pickPhoto = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        startActivityForResult(pickPhoto, GalleryHelper.PICK_IMAGE_REQUEST)
    }

    /**
     * Show network connection message
     */
    private fun showNoNetworkDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("You are not connected to the network")
        builder.setMessage("Please turn on WiFi for best performance")
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun isConnected(): Boolean {
        var connected = false
        try {
            val cm =
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nInfo: NetworkInfo? = cm.activeNetworkInfo
            connected = nInfo != null && nInfo.isConnected
            return connected
        } catch (e: java.lang.Exception) {
            Log.e("Connectivity Exception", e.message)
        }
        return connected
    }
}