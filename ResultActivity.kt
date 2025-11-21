package com.ai.edit.photo.art.activities.resultactivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ai.edit.photo.art.base.BaseActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ai.edit.photo.art.R
import com.ai.edit.photo.art.activities.crop.CropImageActivity
import com.ai.edit.photo.art.activities.main.MainActivity
import com.ai.edit.photo.art.activities.removebackground.RemoveBackgroundActivity
import com.ai.edit.photo.art.activities.removeobj.RemoveActivity
import com.ai.edit.photo.art.activities.resultactivity.dialog.DiscardDialog
import com.ai.edit.photo.art.activities.resultactivity.dialog.RegenBottomSheet
import com.ai.edit.photo.art.activities.resultactivity.dialog.ReportDialog
import com.ai.edit.photo.art.data.model.EditThemeResponse
import com.ai.edit.photo.art.data.service.AIClient
import com.ai.edit.photo.art.databinding.ActivityResultBinding
import com.ai.edit.photo.art.extensions.setOnUnDoubleClickListener
import com.ai.edit.photo.art.view.RomanticLoadingDialog
import com.otaliastudios.cameraview.CameraView.PERMISSION_REQUEST_CODE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ResultActivity : BaseActivity<ActivityResultBinding>(ActivityResultBinding::inflate) {
    private var originalImageUri: Uri? = null
    private var processedImageUrl: String? = null
    private var isFromMergeRomantic: Boolean = false
    private var check: Boolean = false
    private var isReturningFromEmail = false
    private val imageHistory = mutableListOf<String>()
    private var currentIndex = -1
    private lateinit var removeLauncher: ActivityResultLauncher<Intent>
    private lateinit var removeBgLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    private lateinit var imageUri: Uri
    private lateinit var selectedCode: String
    private lateinit var styleselectCode: String
    private var isTheme: Boolean = false

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        showDiscardDialog()
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun setUp() {

        removeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                handleImageUpdateFromRemove(uri)
            }
        }

        removeBgLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                handleImageUpdateFromRemove(uri)
            }
        }
        cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                processedImageUrl = uri.toString()

                if (currentIndex < imageHistory.lastIndex) {
                    imageHistory.subList(currentIndex + 1, imageHistory.size).clear()
                }
                imageHistory.add(uri.toString())
                currentIndex = imageHistory.lastIndex

                loadImageFromHistory(currentIndex)
            }
        }


        styleselectCode = intent.getStringExtra("selected_code") ?: ""
        Log.d("vclvcl", "🚀 selected: $styleselectCode")

        val selectedCodeRomantic = intent.getStringExtra("SELECTED_STYLE")
        Log.d("vclvcl", "🚀 selected: $selectedCodeRomantic")

        val imagePath = intent.getStringExtra("ORIGINAL_IMAGE_PATH")
        val imageUriString = intent.getStringExtra("ORIGINAL_IMAGE_URI")
        val imageUri = if (!imageUriString.isNullOrEmpty()) Uri.parse(imageUriString) else null
        originalImageUri = imageUri
        when {
            imageUri != null && imageUri.toString().startsWith("file:///android_asset/") -> {
                try {
                    val assetPath = imageUri.toString().removePrefix("file:///android_asset/")
                    val inputStream = assets.open(assetPath)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.mainImg.setImageBitmap(bitmap)
                    inputStream.close()
                } catch (e: Exception) {
                    binding.mainImg.setImageResource(R.drawable.ic_swap_fail)
                }
            }

            !imagePath.isNullOrEmpty() -> {
                originalImageUri = Uri.fromFile(File(imagePath))
                val bitmap = BitmapFactory.decodeFile(imagePath)
                binding.mainImg.setImageBitmap(bitmap)
            }
            imageUri != null -> {
                Glide.with(this)
                    .load(imageUri)
                    .centerCrop()
                    .into(binding.mainImg)
            }

            else -> {
                binding.mainImg.setImageResource(R.drawable.ic_swap_fail)
            }
        }
        isFromMergeRomantic = intent.getBooleanExtra("from_merge_romantic", false)
        if (isFromMergeRomantic) {
            binding.btnShowOrigin.visibility = View.GONE
        }
        Log.d("vclvcl", "🚀 selected: $isFromMergeRomantic")
        check = isFromMergeRomantic
        if (isFromMergeRomantic) {
            val styleCodeRomantic = intent.getStringExtra("SELECTED_STYLE")
            Log.d("vclvcl", "🚀 selected: $styleCodeRomantic")
            styleCodeRomantic?.let { code ->
                val item = com.ai.edit.photo.art.activities.romantic.select.RomanticViewModel()
                    .getRomanticList()
                    .find { it.code == code }

                item?.imageUrl?.let { assetPath ->
                    try {
                        val inputStream = assets.open(assetPath)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        binding.mainImg.setImageBitmap(bitmap)
                        inputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        binding.mainImg.setImageResource(R.drawable.ic_error_api)
                    }
                }
            }
        }

        val imageUrl = intent.getStringExtra("PROCESSED_IMAGE_URL") ?: return
        processedImageUrl = imageUrl

        imageHistory.add(imageUrl)
        currentIndex = 0

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_loading_api)
            .error(R.drawable.ic_error_api)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(binding.imgResult)

        binding.ivBack.setOnUnDoubleClickListener {
            showDiscardDialog()
        }
        binding.mainImg.setOnUnDoubleClickListener {  showDiscardDialog() }
        binding.btnSave.setOnUnDoubleClickListener { saveImageToGallery() }
        binding.btnRedraw.setOnUnDoubleClickListener {
            processedImageUrl?.let { previewUrl ->
                val regenBottomSheet = RegenBottomSheet(
                    context = this,
                    previewUrl = previewUrl
                ) {
                    reDraw()
                }
                regenBottomSheet.show(supportFragmentManager, "RegenBottomSheet")
            } ?: Toast.makeText(this, "Không có ảnh để regenerate!", Toast.LENGTH_SHORT).show()
        }


        binding.btnShowOrigin.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    originalImageUri?.let { uri ->
                        if (uri.toString().startsWith("file:///android_asset/")) {
                            val assetPath = uri.toString().removePrefix("file:///android_asset/")
                            try {
                                val inputStream = assets.open(assetPath)
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                binding.imgResult.setImageBitmap(bitmap)
                                inputStream.close()
                            } catch (e: Exception) {
                                binding.imgResult.setImageResource(R.drawable.ic_swap_fail)
                            }
                        } else {
                            Glide.with(this)
                                .load(uri)
                                .centerCrop()
                                .into(binding.imgResult)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    processedImageUrl?.let { url ->
                        Glide.with(this)
                            .load(url)
                            .placeholder(R.drawable.ic_loading_api)
                            .error(R.drawable.ic_error_api)
                            .centerCrop()
                            .into(binding.imgResult)
                    }
                    true
                }

                else -> false
            }
        }

        binding.btnReport.setOnUnDoubleClickListener {
            reportFun()
        }
        binding.btnUndo.setOnUnDoubleClickListener { handleUndo() }
        binding.btnRedo.setOnUnDoubleClickListener { handleRedo() }
        binding.btnReset.setOnUnDoubleClickListener {
            if (imageHistory.isNotEmpty()) {
                currentIndex = 0
                loadImageFromHistory(currentIndex)
            } else {
                Toast.makeText(this, "Không có ảnh để reset!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.removeObj.setOnUnDoubleClickListener {
            processedImageUrl?.let { url ->
                lifecycleScope.launch {
                    val uri = downloadImageToTempFile(url)
                    if (uri != null) {
                        val intent = Intent(this@ResultActivity, RemoveActivity::class.java)
                        intent.data = uri
                        intent.putExtra("is_from_edit", true)
                        removeLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@ResultActivity, "Try Again!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.removeBG.setOnUnDoubleClickListener {
            processedImageUrl?.let { url ->
                lifecycleScope.launch {
                    val uri = downloadImageToTempFile(url)
                    if (uri != null) {
                        val intent = Intent(this@ResultActivity, RemoveBackgroundActivity::class.java)
                        intent.data = uri
                        intent.putExtra("is_from_edit", true)
                        removeBgLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@ResultActivity, "Try Again!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.editPhoto.setOnUnDoubleClickListener {
            processedImageUrl?.let { url ->
                lifecycleScope.launch {
                    val uri = downloadImageToTempFile(url)
                    if (uri != null) {
                        val intent = Intent(this@ResultActivity, CropImageActivity::class.java).apply {
                            data = uri
                            putExtra("is_from_edit", true)
                        }
                        cropImageLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@ResultActivity, "Try Again!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


    }
    private fun handleImageUpdateFromRemove(uri: Uri) {
        processedImageUrl = uri.toString()
        if (currentIndex < imageHistory.lastIndex) {
            imageHistory.subList(currentIndex + 1, imageHistory.size).clear()
        }
        imageHistory.add(uri.toString())
        currentIndex = imageHistory.lastIndex
        loadImageFromHistory(currentIndex)
    }

    private suspend fun downloadImageToTempFile(url: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = Glide.with(this@ResultActivity)
                .asBitmap()
                .load(url)
                .submit()
                .get()

            val fileName = "temp_${System.currentTimeMillis()}.jpg"
            val file = File(cacheDir, fileName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            return@withContext Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun showDiscardDialog() {
        DiscardDialog(this) {
            finish()
        }.show()
    }
    private fun handleUndo() {
        if (currentIndex > 0) {
            currentIndex--
            loadImageFromHistory(currentIndex)
        } else {
            Toast.makeText(this, getString(R.string.no_undo_action), Toast.LENGTH_SHORT).show()
        }
    }
    private fun handleRedo() {
        if (currentIndex < imageHistory.size - 1) {
            currentIndex++
            loadImageFromHistory(currentIndex)
        } else {
            Toast.makeText(this, getString(R.string.no_redo_action), Toast.LENGTH_SHORT).show()
        }
    }
    private fun loadImageFromHistory(index: Int) {
        val url = imageHistory.getOrNull(index)
        if (url != null) {
            processedImageUrl = url
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_loading_api)
                .error(R.drawable.ic_error_api)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(binding.imgResult)
        }
    }

    private fun reportFun() {
        processedImageUrl?.let { url ->
            ReportDialog(this, url, lifecycleScope) {
                isReturningFromEmail = true
            }.show()
        } ?: Toast.makeText(this, "No image to report!", Toast.LENGTH_SHORT).show()
    }

    private fun compressImage(imageUri: Uri): ByteArray {
        val bitmap = if (imageUri.toString().startsWith("file:///android_asset/")) {
            val assetPath = imageUri.toString().replace("file:///android_asset/", "")
            val inputStream = assets.open(assetPath)
            BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
        } else {
            val inputStream = contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(inputStream).also { inputStream?.close() }
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return outputStream.toByteArray()
    }

    private suspend fun applyThemeToImage(imageUri: Uri, themeCode: String): EditThemeResponse {
        val compressedBytes = compressImage(imageUri)
        val requestFile = compressedBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", "image.jpg", requestFile)
        val studioBody = themeCode.toRequestBody("text/plain".toMediaTypeOrNull())
        return withContext(Dispatchers.IO) {
            AIClient.apiService.editTheme("123456", filePart, studioBody)
        }
    }

    private suspend fun applyAnimeCharacterToImage(imageUri: Uri, animeCode: String): EditThemeResponse {
        val compressedBytes = compressImage(imageUri)
        val requestFile = compressedBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", "image.jpg", requestFile)
        val animeCodeBody = animeCode.toRequestBody("text/plain".toMediaTypeOrNull())
        return withContext(Dispatchers.IO) {
            AIClient.apiService.editAnimeCharacterImage("1212", filePart, animeCodeBody)
        }
    }
    private fun createCompressedImagePart(uri: Uri, name: String): MultipartBody.Part {
        val bitmap = if (uri.toString().startsWith("file:///android_asset/")) {
            val assetPath = uri.toString().removePrefix("file:///android_asset/")
            assets.open(assetPath).use {
                BitmapFactory.decodeStream(it)
            }
        } else {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } ?: throw Exception("Failed to decode bitmap from URI: $uri")

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 1080, 1080, true)

        val outputStream = ByteArrayOutputStream()
        var quality = 85
        do {
            outputStream.reset()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 5
        } while (outputStream.size() > 1024 * 1024 && quality > 20)

        val reqBody = outputStream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(name, "$name.jpg", reqBody)
    }


    private fun reDraw() {
        if (isFromMergeRomantic) {
            val uri1 = intent.getStringExtra("FIRST_IMAGE_URI")?.let { Uri.parse(it) } ?: return
            val uri2 = intent.getStringExtra("SECOND_IMAGE_URI")?.let { Uri.parse(it) } ?: return
            val styleCodeRomantic = intent.getStringExtra("SELECTED_STYLE") ?: return
            Log.d("vclvcl", "uri1=$uri1")
            Log.d("vclvcl", "uri2=$uri2")
            Log.d("vclvcl", "styleCode=$styleCodeRomantic")


            lifecycleScope.launch {
                showLoadingDialog()
                try {
                    val part1 = createCompressedImagePart(uri1, "fileFirst")
                    val part2 = createCompressedImagePart(uri2, "fileSecond")
                    val codeBody = styleCodeRomantic.toRequestBody("text/plain".toMediaTypeOrNull())
                    val response = withContext(Dispatchers.IO) {
                        AIClient.apiService.mergeTryOn("1212", part1, part2, codeBody)
                    }

                    val newUrl = response.data.imageUrl ?: return@launch
                    if (currentIndex < imageHistory.lastIndex) {
                        imageHistory.subList(currentIndex + 1, imageHistory.size).clear()
                    }
                    imageHistory.add(newUrl)
                    currentIndex = imageHistory.lastIndex
                    loadImageFromHistory(currentIndex)

                } catch (e: Exception) {
                    Toast.makeText(this@ResultActivity, "Redraw failed (merge)", Toast.LENGTH_SHORT).show()
                } finally {
                    hideLoadingDialog()
                }
            }
        } else {
            imageUri = intent.getStringExtra("ORIGINAL_IMAGE_URI")?.let { Uri.parse(it) } ?: return
            selectedCode = intent.getStringExtra("SELECTED_THEME") ?: return
            Log.d("vclvcl", "🚀 selected: $selectedCode")
            isTheme = intent.getBooleanExtra("IS_GHIBLI_THEME", false)
            Log.d("vclvcl", "🚀 isTheme: $isTheme")

            lifecycleScope.launch {
                showLoadingDialog()
                try {
                    val response = if (isTheme) {
                        applyThemeToImage(imageUri, selectedCode)
                    } else {
                        applyAnimeCharacterToImage(imageUri, selectedCode)
                    }

                    val newUrl = response.data?.imageUrl ?: return@launch
                    if (currentIndex < imageHistory.lastIndex) {
                        imageHistory.subList(currentIndex + 1, imageHistory.size).clear()
                    }
                    imageHistory.add(newUrl)
                    currentIndex = imageHistory.lastIndex
                    loadImageFromHistory(currentIndex)

                } catch (e: Exception) {
                    Toast.makeText(this@ResultActivity, "Redraw failed!", Toast.LENGTH_SHORT).show()
                } finally {
                    hideLoadingDialog()
                }
            }
        }
    }

    private var loadingDialog: RomanticLoadingDialog? = null

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = RomanticLoadingDialog(this)
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun saveImageToGallery() {
        processedImageUrl?.let { imageUrl ->
            lifecycleScope.launch {
                try {
                    if (!hasWritePermission()) {
                        requestWritePermission()
                        return@launch
                    }

                    val bitmap = withContext(Dispatchers.IO) {
                        Glide.with(this@ResultActivity)
                            .asBitmap()
                            .load(imageUrl)
                            .submit()
                            .get()
                    }

                    // Tạo ảnh có tag
                    val taggedBitmap = drawStyledTagOnBitmap(bitmap, "Ai Photo Editor")

                    withContext(Dispatchers.IO) {
                        // LƯU ẢNH CÓ TAG VÀO THƯ VIỆN (return URI)
                        val savedUri = saveImageToGalleryInFolder(taggedBitmap)

                        // TẠO ẢNH GỐC TẠM (return URI)
                        val originalTempUri = saveOriginalImageTemp(bitmap)

                        // DEBUG LOGS
                        Log.d("ResultActivity", "savedUri after save: $savedUri")
                        Log.d("ResultActivity", "originalTempUri after save: $originalTempUri")

                        withContext(Dispatchers.Main) {
                            if (savedUri != null && originalTempUri != null) {
                                Toast.makeText(this@ResultActivity, getString(R.string.photo_save), Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@ResultActivity, ViewResultActivity::class.java).apply {
                                    putExtra("TAGGED_IMAGE_URI", savedUri.toString())
                                    putExtra("ORIGINAL_IMAGE_URL", processedImageUrl)
                                    putExtra("ORIGINAL_TEMP_URI", originalTempUri.toString())
                                    putExtra("CHECK", check)
                                    putExtra("SELECTED_STYLE", intent.getStringExtra("SELECTED_STYLE"))
                                    putExtra("selected_code", styleselectCode)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                // DEBUG LOGS trước khi start activity
                                Log.d("ResultActivity", "Starting ViewResultActivity with:")
                                Log.d("ResultActivity", "  TAGGED_IMAGE_URI: ${savedUri.toString()}")
                                Log.d("ResultActivity", "  ORIGINAL_TEMP_URI: ${originalTempUri.toString()}")

                                startActivity(intent)
                                finish()
                            } else {
                                Log.e("ResultActivity", "Failed to save images - savedUri: $savedUri, originalTempUri: $originalTempUri")
                                Toast.makeText(this@ResultActivity, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("ResultActivity", "Save failed: ${e.message}", e)
                    Toast.makeText(this@ResultActivity, "Save failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun saveOriginalImageTemp(bitmap: Bitmap): Uri? {
        return try {
            val tempFile = File(cacheDir, "temp_original_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val uri = Uri.fromFile(tempFile)
            Log.d("ResultActivity", "Original temp saved to: $uri")
            uri
        } catch (e: Exception) {
            Log.e("ResultActivity", "Failed to save original temp: ${e.message}", e)
            null
        }
    }


    private fun saveImageToGalleryInFolder(bitmap: Bitmap): Uri? {
        val filename = "AI_${System.currentTimeMillis()}.jpg"
        val folderName = "Ai Photo Editor"
        val subFolderName = intent.getStringExtra("SELECTED_STYLE") ?: styleselectCode

        val resolver = contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName/$subFolderName")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d("ResultActivity", "Image saved successfully to: $uri")
                uri // ← QUAN TRỌNG: Return URI
            }
        } else {
            val imageDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$folderName/$subFolderName")
            if (!imageDir.exists()) imageDir.mkdirs()

            val imageFile = File(imageDir, filename)
            FileOutputStream(imageFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
            }

            MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), null, null)
            val fileUri = Uri.fromFile(imageFile)

            Log.d("ResultActivity", "Image saved successfully to: $fileUri")
            fileUri // ← QUAN TRỌNG: Return URI
        }
    }



    private fun drawStyledTagOnBitmap(bitmap: Bitmap, text: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bitmap.width / 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val marginRight = 40f
        val marginBottom = 40f

        val x = result.width - bounds.width() - marginRight
        val y = result.height - marginBottom

        canvas.drawText(text, x, y, paint)

        return result
    }

    private fun hasWritePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestWritePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE + 1
            )
        }
    }
}
