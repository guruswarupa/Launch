package com.guruswarupa.launch.utils

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

data class ContributionData(
    val contributions: Map<String, Int>,
    val totalContributions: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastUpdated: String,
    val year: Int = LocalDate.now().year
)

class GithubApiService(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    fun fetchContributionData(username: String, token: String, year: Int = LocalDate.now().year): ContributionData {

        val graphqlQuery = """
            {
              user(login: "$username") {
                contributionsCollection(from: "${year}-01-01T00:00:00Z", to: "${year}-12-31T23:59:59Z") {
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays {
                        contributionCount
                        date
                      }
                    }
                  }
                  contributionYears
                }
              }
            }
        """.trimIndent()
        
        val apiUrl = "https://api.github.com/graphql"
        val postData = JSONObject()
        postData.put("query", graphqlQuery)

        var connection: HttpsURLConnection? = null

        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Launch-App")
            connection.doOutput = true


            val outputStream = connection.outputStream
            outputStream.write(postData.toString().toByteArray())
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)


                val data = jsonResponse.getJSONObject("data")
                val user = data.getJSONObject("user")
                val contributionsCollection = user.getJSONObject("contributionsCollection")
                val contributionCalendar = contributionsCollection.getJSONObject("contributionCalendar")

                val totalContributions = contributionCalendar.getInt("totalContributions")


                val weeksArray = contributionCalendar.getJSONArray("weeks")
                val contributions = mutableMapOf<String, Int>()

                for (i in 0 until weeksArray.length()) {
                    val weekObject = weeksArray.getJSONObject(i)
                    val daysArray = weekObject.getJSONArray("contributionDays")

                    for (j in 0 until daysArray.length()) {
                        val dayObject = daysArray.getJSONObject(j)
                        val date = dayObject.getString("date")
                        val count = dayObject.getInt("contributionCount")

                        contributions[date] = count
                    }
                }


                val streaks = calculateStreaks(contributions)

                return ContributionData(
                    contributions = contributions,
                    totalContributions = totalContributions,
                    currentStreak = streaks.first,
                    longestStreak = streaks.second,
                    lastUpdated = LocalDate.now().toString()
                )
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                throw IOException("HTTP Error: $responseCode - $errorResponse")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun calculateStreaks(contributions: Map<String, Int>): Pair<Int, Int> {

        val sortedDates = contributions.keys.map { LocalDate.parse(it) }.sorted()

        var currentStreak = 0
        var maxStreak = 0
        var tempStreak = 0

        for (date in sortedDates) {
            val count = contributions[date.toString()] ?: 0

            if (count > 0) {
                tempStreak++
                currentStreak = tempStreak
            } else {
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
                tempStreak = 0
            }
        }


        if (tempStreak > maxStreak) {
            maxStreak = tempStreak
        }

        return Pair(currentStreak, maxStreak)
    }


    fun getTodaysContributions(username: String, token: String, year: Int = LocalDate.now().year): Int {
        val today = LocalDate.now().toString()
        val data = fetchContributionData(username, token, year)
        return data.contributions[today] ?: 0
    }


    fun getThisWeeksContributions(username: String, token: String, year: Int = LocalDate.now().year): Int {
        val data = fetchContributionData(username, token, year)
        val thisWeekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value - 1L)

        var weeklyTotal = 0
        for (i in 0..6) {
            val date = thisWeekStart.plusDays(i.toLong()).toString()
            weeklyTotal += data.contributions[date] ?: 0
        }

        return weeklyTotal
    }


    fun getAvailableContributionYears(username: String, token: String): List<Int> {
        val graphqlQuery = """
            {
              user(login: "$username") {
                contributionsCollection {
                  contributionYears
                }
              }
            }
        """.trimIndent()

        val apiUrl = "https://api.github.com/graphql"
        val postData = JSONObject()
        postData.put("query", graphqlQuery)

        var connection: HttpsURLConnection? = null

        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Launch-App")
            connection.doOutput = true


            val outputStream = connection.outputStream
            outputStream.write(postData.toString().toByteArray())
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)


                val data = jsonResponse.getJSONObject("data")
                val user = data.getJSONObject("user")
                val contributionsCollection = user.getJSONObject("contributionsCollection")
                val yearsArray = contributionsCollection.getJSONArray("contributionYears")

                val years = mutableListOf<Int>()
                for (i in 0 until yearsArray.length()) {
                    years.add(yearsArray.getInt(i))
                }

                return years.sortedDescending()
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                throw IOException("HTTP Error: $responseCode - $errorResponse")
            }
        } finally {
            connection?.disconnect()
        }
    }
}