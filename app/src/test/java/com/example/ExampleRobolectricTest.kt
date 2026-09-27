package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PlaceCategory
import com.example.data.repository.MapRepositoryImpl
import com.example.di.DefaultAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    assertEquals("MapFlow", appName)
  }

  @Test
  fun `repository returns places via Kotlin Flow`() = runTest {
    val container = DefaultAppContainer()
    val repo = container.mapRepository
    val places = repo.getPlacesStream(PlaceCategory.ALL, "").first()
    assertTrue(places.isNotEmpty())
  }
}

