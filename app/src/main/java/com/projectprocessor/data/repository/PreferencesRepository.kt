package com.projectprocessor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.projectprocessor.data.model.InputType
import com.projectprocessor.data.model.ProcessConfig
import com.projectprocessor.data.model.XmlMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val INPUT_TYPE = stringPreferencesKey("input_type")
        val INPUT_PATH = stringPreferencesKey("input_path")
        val OUTPUT_DIR = stringPreferencesKey("output_dir")
        val SPLIT_MB = intPreferencesKey("split_mb")
        val PROCESS_CODE = booleanPreferencesKey("process_code")
        val PROCESS_ADVANCED = booleanPreferencesKey("process_advanced")
        val PROCESS_XML = booleanPreferencesKey("process_xml")
        val XML_MODE = stringPreferencesKey("xml_mode")
        val XML_SUBDIRS = stringPreferencesKey("xml_subdirs")
        val CODE_EXTRAS = stringPreferencesKey("code_extras")
        val CUSTOM_EXTS = stringPreferencesKey("custom_exts")
        val AUTO_PROCESS = booleanPreferencesKey("auto_process")
        val AUTO_OPEN_OUTPUT_DIR = booleanPreferencesKey("auto_open_output_dir")
        val AUTO_OPEN_TEXT_FILE = booleanPreferencesKey("auto_open_text_file")
        val SEPARATE_XML_OUTPUT = booleanPreferencesKey("separate_xml_output")
    }

    val configFlow: Flow<ProcessConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ProcessConfig(
                inputType = InputType.valueOf(
                    preferences[PreferencesKeys.INPUT_TYPE] ?: InputType.ZIP.name
                ),
                inputPath = preferences[PreferencesKeys.INPUT_PATH] ?: "",
                outputDir = preferences[PreferencesKeys.OUTPUT_DIR] ?: "",
                splitMb = preferences[PreferencesKeys.SPLIT_MB] ?: 10,
                processCode = preferences[PreferencesKeys.PROCESS_CODE] ?: true,
                processAdvanced = preferences[PreferencesKeys.PROCESS_ADVANCED] ?: true,
                processXml = preferences[PreferencesKeys.PROCESS_XML] ?: true,
                xmlMode = try {
                    XmlMode.valueOf(preferences[PreferencesKeys.XML_MODE] ?: XmlMode.SMART.name)
                } catch (e: IllegalArgumentException) {
                    XmlMode.SMART
                },
                xmlSubdirs = preferences[PreferencesKeys.XML_SUBDIRS]?.let {
                    it.split(",").filter { s -> s.isNotEmpty() }.toSet()
                } ?: setOf("layout", "navigation", "xml"),
                codeExtras = preferences[PreferencesKeys.CODE_EXTRAS] ?: "",
                customExts = preferences[PreferencesKeys.CUSTOM_EXTS] ?: "",
                autoProcess = preferences[PreferencesKeys.AUTO_PROCESS] ?: false,
                autoOpenOutputDir = preferences[PreferencesKeys.AUTO_OPEN_OUTPUT_DIR] ?: false,
                autoOpenTextFile = preferences[PreferencesKeys.AUTO_OPEN_TEXT_FILE] ?: false,
                separateXmlOutput = preferences[PreferencesKeys.SEPARATE_XML_OUTPUT] ?: false
            )
        }

    suspend fun saveConfig(config: ProcessConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INPUT_TYPE] = config.inputType.name
            preferences[PreferencesKeys.INPUT_PATH] = config.inputPath
            preferences[PreferencesKeys.OUTPUT_DIR] = config.outputDir
            preferences[PreferencesKeys.SPLIT_MB] = config.splitMb
            preferences[PreferencesKeys.PROCESS_CODE] = config.processCode
            preferences[PreferencesKeys.PROCESS_ADVANCED] = config.processAdvanced
            preferences[PreferencesKeys.PROCESS_XML] = config.processXml
            preferences[PreferencesKeys.XML_MODE] = config.xmlMode.name
            preferences[PreferencesKeys.XML_SUBDIRS] = config.xmlSubdirs.joinToString(",")
            preferences[PreferencesKeys.CODE_EXTRAS] = config.codeExtras
            preferences[PreferencesKeys.CUSTOM_EXTS] = config.customExts
            preferences[PreferencesKeys.AUTO_PROCESS] = config.autoProcess
            preferences[PreferencesKeys.AUTO_OPEN_OUTPUT_DIR] = config.autoOpenOutputDir
            preferences[PreferencesKeys.AUTO_OPEN_TEXT_FILE] = config.autoOpenTextFile
            preferences[PreferencesKeys.SEPARATE_XML_OUTPUT] = config.separateXmlOutput
        }
    }
}
