package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BloxVerse", appName)
  }

  @Test
  fun `test apk interactive runner generation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testGame = com.example.data.model.Game(
      id = "test_apk_1",
      title = "Cyber Racer",
      gameUrl = "apkbase64:mockdata",
      gameType = "apk",
      apkPackageName = "com.example.cyberracer"
    )
    val html = com.example.data.GameZipExtractor.resolvePlayableHtmlForApk(context, testGame)
    org.junit.Assert.assertTrue("HTML should contain Cyber Racer", html.contains("Cyber Racer"))
    org.junit.Assert.assertTrue("HTML should have canvas", html.contains("<canvas"))
    org.junit.Assert.assertTrue("HTML should have JUMP button", html.contains("JUMP"))
  }

  @Test
  fun `test decompressIfNeeded on plain text`() {
    val plainHtml = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>"
    val result = com.example.data.GameZipExtractor.decompressIfNeeded(plainHtml)
    assertEquals(plainHtml, result)
  }
}
