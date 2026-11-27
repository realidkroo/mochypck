// human-authored pack.json specification compiled into a binary .mochypck index by PackCompiler

package com.mochyy.mochypck.compiler

import com.mochyy.mochymusic.pack.PackChoiceSpec
import com.mochyy.mochymusic.pack.PackFieldSpec
import kotlinx.serialization.Serializable

@Serializable
data class PackSourceSpec(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int = 1,
    val description: String = "",
    val author: String = "",
    val sourceUrl: String? = null,
    val minAppVersion: String? = null,
    val icon: String? = null,
    val banner: String? = null,
    val assetsDir: String? = null,
    val providers: List<ProviderSourceSpec> = emptyList(),
)

@Serializable
data class ProviderSourceSpec(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val description: String = "",
    val capabilities: List<String> = emptyList(),
    val entryClass: String,
    val defaultPriority: Int = 100,
    val requiresAccount: Boolean = false,
    val isLastResort: Boolean = false,
    val enabledByDefault: Boolean = true,
    val icon: String? = null,
    val banner: String? = null,
    val projectName: String? = null,
    val projectUrl: String? = null,
    val websiteUrl: String? = null,
    val choices: List<PackChoiceSpec> = emptyList(),
    val fields: List<PackFieldSpec> = emptyList(),
)
