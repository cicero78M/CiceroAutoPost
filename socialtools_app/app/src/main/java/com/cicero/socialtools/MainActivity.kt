package com.cicero.socialtools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fragments = listOf<Fragment>(
            InstaOauthLoginFragment(),
            InstagramToolsFragment()
        )

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        viewPager.isUserInputEnabled = false

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = when (item.itemId) {
                R.id.nav_insta_login -> 0
                R.id.nav_instagram_tools -> 1
                else -> 0
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selected = when (position) {
                    0 -> R.id.nav_insta_login
                    else -> R.id.nav_instagram_tools
                }
                if (bottomNav.selectedItemId != selected) {
                    bottomNav.selectedItemId = selected
                }
            }
        })

        bottomNav.selectedItemId = R.id.nav_insta_login
    }
}
