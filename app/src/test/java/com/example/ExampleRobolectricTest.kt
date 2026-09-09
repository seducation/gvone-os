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
    assertEquals("GVONE Browser", appName)
  }

  @Test
  fun `browser view model initializes without crashing`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.BrowserViewModel(app)
    org.junit.Assert.assertNotNull(viewModel)
    org.junit.Assert.assertNotNull(viewModel.browserController)
    org.junit.Assert.assertNotNull(viewModel.cns)
    org.junit.Assert.assertTrue(viewModel.tabs.value.isNotEmpty())
    org.junit.Assert.assertNotNull(viewModel.getActiveTab())
    org.junit.Assert.assertEquals("personal", viewModel.environmentManager.currentEnvironment.value.id)
  }

  @Test
  fun `main activity launches without crashing`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    controller.setup()
    val activity = controller.get()
    org.junit.Assert.assertNotNull(activity)
  }
}
