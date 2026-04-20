package com.example.fingerdetector.base

interface PermissionAware {
    fun onPermissionResult(allGranted: Boolean, results: Map<String, Boolean>)
}
