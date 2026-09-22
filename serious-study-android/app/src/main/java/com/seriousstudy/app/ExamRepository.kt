package com.seriousstudy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Exam(
    val id: String,
    val name: String,
    val subject: String,
    val date: String  // "YYYY-MM-DD"
)

object ExamRepository {

    private const val PREF_NAME = "exams"
    private const val KEY_EXAMS = "exams_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getExams(context: Context): List<Exam> {
        val json = prefs(context).getString(KEY_EXAMS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = mutableListOf<Exam>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                Exam(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    subject = obj.getString("subject"),
                    date = obj.getString("date")
                )
            )
        }
        return result
    }

    fun saveExam(context: Context, exam: Exam) {
        val list = getExams(context).toMutableList()
        list.add(exam)
        persistList(context, list)
    }

    fun deleteExam(context: Context, id: String) {
        val list = getExams(context).filter { it.id != id }
        persistList(context, list)
    }

    private fun persistList(context: Context, list: List<Exam>) {
        val arr = JSONArray()
        list.forEach { exam ->
            val obj = JSONObject()
            obj.put("id", exam.id)
            obj.put("name", exam.name)
            obj.put("subject", exam.subject)
            obj.put("date", exam.date)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_EXAMS, arr.toString()).apply()
    }
}
