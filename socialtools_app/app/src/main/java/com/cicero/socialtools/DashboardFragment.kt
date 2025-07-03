package com.cicero.socialtools

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recycler_posts)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = PostAdapter(emptyList())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val posts = InstagramGraphApi.fetchRecentPosts()
            adapter.update(posts)
            view.findViewById<View>(R.id.progress_loading).visibility = View.GONE
            view.findViewById<View>(R.id.text_empty).visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
