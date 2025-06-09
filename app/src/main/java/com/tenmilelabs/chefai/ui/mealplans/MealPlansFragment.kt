package com.tenmilelabs.chefai.ui.mealplans

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tenmilelabs.chefai.databinding.FragmentMealplansBinding

class MealPlansFragment : Fragment() {

    private var _binding: FragmentMealplansBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel: MealPlansViewModel by viewModels()

        _binding = FragmentMealplansBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textMealplans
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}