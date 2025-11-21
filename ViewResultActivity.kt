package com.ai.edit.photo.art.activities.resultactivity

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ai.edit.photo.art.R
import com.bumptech.glide.Glide
import com.ai.edit.photo.art.activities.category.CategoryActivity
import com.ai.edit.photo.art.activities.main.MainActivity
import com.ai.edit.photo.art.activities.romantic.select.CategoryRomanticActivity
import com.ai.edit.photo.art.activities.hotitem.HotItemViewModel
import com.ai.edit.photo.art.activities.hotitem.HotItemAdapter
import com.ai.edit.photo.art.activities.hotitem.HotItem
import com.ai.edit.photo.art.activities.resultactivity.dialog.WatermarkRewardDialog
import com.ai.edit.photo.art.activities.romantic.merge.MergeRomanticActivity
import com.ai.edit.photo.art.activities.selectface.CostumeForGuysActivity
import com.ai.edit.photo.art.activities.selectimg.SelectImageActivity
import com.ai.edit.photo.art.base.BaseActivity
import com.ai.edit.photo.art.databinding.ActivityViewResultBinding
import com.ai.edit.photo.art.extensions.setOnUnDoubleClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ViewResultActivity : BaseActivity<ActivityViewResultBinding>(ActivityViewResultBinding::inflate) {

    private var check: Boolean = false
    private lateinit var hotItemViewModel: HotItemViewModel
    private lateinit var hotItemAdapter: HotItemAdapter

    // URI ảnh có tag đã lưu vào thư viện
    private var taggedImageUriString: String? = null
    // URL ảnh gốc từ server
    private var originalImageUrl: String? = null
    // URI ảnh gốc tạm (không có tag) để remove watermark
    private var originalTempUriString: String? = null

    private var isRemoveBg: Boolean = false
    private var isRemoveObj: Boolean = false
    private var styleCode: String? = null
    private var styleselectCode: String? = null
    private var watermarkRewardDialog: WatermarkRewardDialog? = null

    // Trạng thái watermark
    private var isWatermarkRemoved = false

    override fun setUp() {
        // Nhận URI các ảnh từ ResultActivity
        taggedImageUriString = intent.getStringExtra("TAGGED_IMAGE_URI")
        originalImageUrl = intent.getStringExtra("ORIGINAL_IMAGE_URL")
        originalTempUriString = intent.getStringExtra("ORIGINAL_TEMP_URI")

        Log.d("ViewResultActivity", "taggedImageUriString: $taggedImageUriString")
        Log.d("ViewResultActivity", "originalImageUrl: $originalImageUrl")
        Log.d("ViewResultActivity", "originalTempUriString: $originalTempUriString")

        // Các thông tin khác
        check = intent.getBooleanExtra("CHECK", false)
        isRemoveBg = intent.getBooleanExtra("isRemoveBg", false)
        isRemoveObj = intent.getBooleanExtra("isRemoveObj", false)
        styleCode = intent.getStringExtra("SELECTED_STYLE")
        styleselectCode = intent.getStringExtra("selected_code")

        // Hiển thị ảnh có tag đã lưu (mặc định)
        showTaggedImage()

        // Setup các nút
        binding.btnMakeAnother.setOnUnDoubleClickListener { nextAnother() }
        binding.btnHome.setOnUnDoubleClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
        binding.btnShare.setOnUnDoubleClickListener { shareCurrentImage() }
        binding.removeWatermark.setOnUnDoubleClickListener { toggleWatermarkAction() }

        setupHotItemsRecyclerView()

        // Cập nhật trạng thái nút remove watermark ban đầu
        updateRemoveWatermarkButtonVisibility()
    }

    private fun showTaggedImage() {
        Log.d("ViewResultActivity", "showTaggedImage called")

        taggedImageUriString?.let { uriString ->
            Log.d("ViewResultActivity", "Loading tagged image: $uriString")

            val imageUri = Uri.parse(uriString)

            Glide.with(this@ViewResultActivity)
                .load(imageUri)
                .placeholder(R.drawable.ic_loading_api)
                .error(R.drawable.ic_error)
                .centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e("ViewResultActivity", "Failed to load tagged image: ${e?.message}")
                        Toast.makeText(this@ViewResultActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d("ViewResultActivity", "Tagged image loaded successfully")
                        return false
                    }
                })
                .into(binding.imgResultView)

            isWatermarkRemoved = false
            updateRemoveWatermarkButtonVisibility()
        } ?: run {
            Log.e("ViewResultActivity", "taggedImageUriString is null!")
            Toast.makeText(this, "No tagged image available", Toast.LENGTH_SHORT).show()

            // Fallback: Thử hiển thị ảnh gốc từ URL
            originalImageUrl?.let { url ->
                Log.d("ViewResultActivity", "Fallback: Loading from originalImageUrl: $url")
                Glide.with(this@ViewResultActivity)
                    .load(url)
                    .placeholder(R.drawable.ic_loading_api)
                    .error(R.drawable.ic_error)
                    .centerCrop()
                    .into(binding.imgResultView)
            }
        }
    }

    private fun showOriginalImage() {
        originalTempUriString?.let { uriString ->
            val imageUri = Uri.parse(uriString)
            Glide.with(this@ViewResultActivity)
                .load(imageUri)
                .error(R.drawable.ic_error)
                .into(binding.imgResultView)

            isWatermarkRemoved = true
            updateRemoveWatermarkButtonVisibility()
        }
    }

    private fun toggleWatermarkAction() {
        if (isWatermarkRemoved) {
            showTaggedImage()
            Toast.makeText(this, "Watermark restored (display only)", Toast.LENGTH_SHORT).show()
        } else {
            showWatermarkRewardDialog()
        }
    }

    // Thêm phương thức này để cập nhật visibility của nút remove watermark
    private fun updateRemoveWatermarkButtonVisibility() {
        if (isWatermarkRemoved) {
            binding.removeWatermark.visibility = View.GONE
        } else {
            binding.removeWatermark.visibility = View.VISIBLE
        }
    }

    private fun showWatermarkRewardDialog() {
        watermarkRewardDialog = WatermarkRewardDialog(this) {
            // Trực tiếp thực hiện remove watermark khi user bấm btnConfirm
            removeWatermarkAndSave()
        }
        watermarkRewardDialog?.show()
    }

    private fun removeWatermarkAndSave() {
        originalTempUriString?.let { tempUri ->
            lifecycleScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        Glide.with(this@ViewResultActivity)
                            .asBitmap()
                            .load(Uri.parse(tempUri))
                            .submit()
                            .get()
                    }

                    withContext(Dispatchers.IO) {
                        val newSavedUri = saveImageWithoutWatermark(bitmap)

                        withContext(Dispatchers.Main) {
                            if (newSavedUri != null) {
                                showOriginalImage()
                                Toast.makeText(this@ViewResultActivity, "Image saved without watermark", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@ViewResultActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ViewResultActivity, "Failed to remove watermark", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        } ?: run {
            Toast.makeText(this, "Original image not available", Toast.LENGTH_SHORT).show()
        }
    }


    private suspend fun saveImageWithoutWatermark(bitmap: Bitmap): Uri? {
        val timestamp = System.currentTimeMillis()
        val filename = "AI_Clean_$timestamp.jpg"
        val folderName = "Ai Photo Editor"
        val subFolderName = "Clean Images" // Thư mục riêng cho ảnh không watermark

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
                uri
            }
        } else {
            val imageDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "$folderName/$subFolderName")
            if (!imageDir.exists()) imageDir.mkdirs()

            val imageFile = File(imageDir, filename)
            FileOutputStream(imageFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
            }

            MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), null, null)
            Uri.fromFile(imageFile)
        }
    }

    private fun shareCurrentImage() {
        // Share ảnh đang hiển thị
        val currentImageUri = if (isWatermarkRemoved) {
            originalTempUriString
        } else {
            taggedImageUriString
        }

        currentImageUri?.let {
            shareImageFromPictures(it)
        } ?: run {
            Toast.makeText(this, "No image to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImageFromPictures(imagePath: String) {
        try {
            val parsedUri = Uri.parse(imagePath)
            Log.d("ShareDebug", "Parsed URI = $parsedUri")

            val uri = when (parsedUri.scheme) {
                "file" -> {
                    val file = File(parsedUri.path ?: return)
                    if (!file.exists()) {
                        Toast.makeText(this, "Ảnh không tồn tại", Toast.LENGTH_SHORT).show()
                        return
                    }
                    FileProvider.getUriForFile(this, "${packageName}.provider", file)
                }
                "content" -> parsedUri
                else -> {
                    Toast.makeText(this, "Định dạng ảnh không hỗ trợ", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể chia sẻ ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    // Các hàm khác giữ nguyên
    private fun setupHotItemsRecyclerView() {
        Log.d("ViewResultActivity", "Setting up RecyclerView...")
        hotItemViewModel = ViewModelProvider(this)[HotItemViewModel::class.java]
        hotItemAdapter = HotItemAdapter { hotItem ->
            handleHotItemClick(hotItem)
        }
        binding.rcvTrending.apply {
            layoutManager = LinearLayoutManager(this@ViewResultActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = hotItemAdapter
        }
        hotItemViewModel.hotItems.observe(this) { items ->
            Log.d("ViewResultActivity", "Received ${items.size} items from ViewModel")
            hotItemAdapter.updateItems(items)
        }
        Log.d("ViewResultActivity", "Loading hot items...")
        hotItemViewModel.loadHotItems(this)
    }

    private fun handleHotItemClick(hotItem: HotItem) {
        val intent = Intent(this, CostumeForGuysActivity::class.java)
        intent.putExtra("SELECTED_CATEGORY", hotItem.category)
        intent.putExtra("selected_code", hotItem.code)
        startActivity(intent)
    }

    private fun nextAnother() {
        if (isRemoveBg) {
            val intent = Intent(this, SelectImageActivity::class.java)
            intent.putExtra("isRemoveBg", true)
            startActivity(intent)
            finish()
            return
        }
        if (isRemoveObj) {
            val intent = Intent(this, SelectImageActivity::class.java)
            intent.putExtra("isRemoveObj", true)
            startActivity(intent)
            finish()
            return
        }
        //romantic
        if (check) {
            val intent = Intent(this, MergeRomanticActivity::class.java)
            intent.putExtra("style", styleCode)
            startActivity(intent)
            finish()
            return
        } else {
            val intent = Intent(this, SelectImageActivity::class.java)
            intent.putExtra("selected_code", styleselectCode)
            startActivity(intent)
            finish()
            return
        }
    }
}
