package com.seriousstudy.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seriousstudy.app.databinding.ActivityExamTrackerBinding
import com.seriousstudy.app.databinding.ItemExamBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.UUID

class ExamTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamTrackerBinding
    private lateinit var adapter: ExamAdapter
    private var exams = mutableListOf<Exam>()
    private var selectedDate: LocalDate = LocalDate.now().plusDays(7)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        exams = ExamRepository.getExams(this).toMutableList()
        adapter = ExamAdapter(exams) { exam ->
            ExamRepository.deleteExam(this, exam.id)
            exams.clear()
            exams.addAll(ExamRepository.getExams(this))
            adapter.notifyDataSetChanged()
        }

        binding.recyclerExams.layoutManager = LinearLayoutManager(this)
        binding.recyclerExams.adapter = adapter

        binding.fabAddExam.setOnClickListener { showAddExamDialog() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showAddExamDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }

        val etName = EditText(context).apply { hint = "Exam name (e.g. Final Exam)" }
        val etSubject = EditText(context).apply { hint = "Subject (e.g. Mathematics)" }
        val tvDate = TextView(context).apply {
            text = "Date: ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.accent))
            val v = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, v, 0, v)
        }

        selectedDate = LocalDate.now().plusDays(7)
        tvDate.text = "Date: ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    tvDate.text = "Date: ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        layout.addView(etName)
        layout.addView(etSubject)
        layout.addView(tvDate)

        AlertDialog.Builder(context)
            .setTitle("Add Exam")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val subject = etSubject.text.toString().trim()
                if (name.isEmpty() || subject.isEmpty()) {
                    Toast.makeText(context, "Name and subject are required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val exam = Exam(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    subject = subject,
                    date = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                )
                ExamRepository.saveExam(context, exam)
                exams.clear()
                exams.addAll(ExamRepository.getExams(context))
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Adapter ────────────────────────────────────────────────────────────────

    inner class ExamAdapter(
        private val items: MutableList<Exam>,
        private val onDelete: (Exam) -> Unit
    ) : RecyclerView.Adapter<ExamAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemExamBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemExamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exam = items[position]
            holder.binding.tvExamName.text = exam.name
            holder.binding.tvExamSubject.text = exam.subject
            holder.binding.tvExamDate.text = exam.date

            val daysLeft = try {
                val examDate = LocalDate.parse(exam.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ChronoUnit.DAYS.between(LocalDate.now(), examDate)
            } catch (_: Exception) {
                0L
            }

            holder.binding.tvDaysRemaining.text = when {
                daysLeft < 0 -> "Past"
                daysLeft == 0L -> "Today"
                else -> "${daysLeft}d"
            }

            val badgeColor = when {
                daysLeft < 0 -> R.color.muted
                daysLeft <= 3 -> R.color.danger
                daysLeft <= 7 -> R.color.accent
                else -> R.color.success
            }
            holder.binding.tvDaysRemaining.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(holder.itemView.context, badgeColor)
                )

            holder.binding.btnDeleteExam.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Delete Exam")
                    .setMessage("Delete \"${exam.name}\"?")
                    .setPositiveButton("Delete") { _, _ -> onDelete(exam) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
