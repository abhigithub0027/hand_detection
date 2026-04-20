package com.example.fingerdetector.base

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.fingerdetector.R
import com.example.fingerdetector.databinding.ActivityMainBinding

open class BaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var permissionAware: PermissionAware? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionAware?.onPermissionResult(result.values.all { it }, result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun showRoot(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigate(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .apply { if (addToBackStack) addToBackStack(fragment::class.java.simpleName) }
            .commit()
    }

    fun requestAppPermissions(requester: PermissionAware, permissions: Array<String>) {
        permissionAware = requester
        permissionLauncher.launch(permissions)
    }

    fun configureToolbar(title: String, showBack: Boolean) {
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
    }
}
