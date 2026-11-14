package com.orbit.tv.launcher.features.home.repository

import android.content.Context
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.features.home.model.MediaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Multi-tier Repository for trending Movies and TV Series.
 * Tier 1: TMDb API (localized titles, 4K/HD posters, ratings, synopsis in system language).
 * Tier 2: Cinemeta open catalog (unblocked global fallback without API keys).
 * Tier 3: Local disk cache (offline persistence).
 * Tier 4: Curated baseline movies & series (zero-state safety net).
 */
class MediaRepository(private val context: Context) {

    private val appPrefs = com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(context)

    private fun getMoviesCacheFile(lang: String): File =
        File(context.cacheDir, "cached_movies_${lang.replace('-', '_')}.json")

    private fun getSeriesCacheFile(lang: String): File =
        File(context.cacheDir, "cached_series_${lang.replace('-', '_')}.json")

    fun getEffectiveLanguage(): String {
        val pref = appPrefs.getContentLanguage()
        if (pref.isNotBlank() && pref != "SYSTEM") {
            return pref.lowercase(Locale.ROOT)
        }
        return getSystemLanguage()
    }

    private fun getSystemLanguage(): String {
        val lang = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.language
            }
        } catch (_: Exception) {
            Locale.getDefault().language
        }
        return lang.lowercase(Locale.ROOT)
    }

    fun getTmdbLanguage(): String {
        return when (getEffectiveLanguage()) {
            "ru", "ru-ru", "be", "kk" -> "ru-RU"
            "uk", "uk-ua" -> "uk-UA"
            "pl", "pl-pl" -> "pl-PL"
            "de", "de-de" -> "de-DE"
            "fr", "fr-fr" -> "fr-FR"
            "es", "es-es" -> "es-ES"
            "it", "it-it" -> "it-IT"
            "pt", "pt-pt", "pt-br" -> "pt-PT"
            "tr", "tr-tr" -> "tr-TR"
            "zh", "zh-cn" -> "zh-CN"
            "ja", "ja-jp" -> "ja-JP"
            "ko", "ko-kr" -> "ko-KR"
            "ar" -> "ar-SA"
            else -> "en-US"
        }
    }

    suspend fun getTrendingMovies(forceRefresh: Boolean = false): List<MediaModel> = withContext(Dispatchers.IO) {
        val tmdbLang = getTmdbLanguage()
        val cacheFile = getMoviesCacheFile(tmdbLang)
        val tmdbUrl = "$TMDB_BASE_URL/trending/movie/week?api_key=$TMDB_API_KEY&language=$tmdbLang"

        // Tier 1: Instant load from disk cache if already cached for this language and not forcing refresh
        if (!forceRefresh && cacheFile.exists()) {
            try {
                val cached = cacheFile.readText()
                val parsedTmdb = parseTmdbJson(cached, isSeries = false)
                if (parsedTmdb.isNotEmpty()) return@withContext parsedTmdb
            } catch (_: Exception) {}
        }

        // Tier 2: TMDb API Network Fetch
        val tmdbData = fetchFromNetwork(tmdbUrl)
        if (tmdbData != null) {
            val parsed = parseTmdbJson(tmdbData, isSeries = false)
            if (parsed.isNotEmpty()) {
                try {
                    cacheFile.writeText(tmdbData)
                } catch (_: Exception) {}
                return@withContext parsed
            }
        }

        // Tier 3: Local Disk Cache Fallback (if network failed during forceRefresh)
        if (cacheFile.exists()) {
            try {
                val cached = cacheFile.readText()
                val parsedTmdb = parseTmdbJson(cached, isSeries = false)
                if (parsedTmdb.isNotEmpty()) return@withContext parsedTmdb
                val parsedCinemeta = parseCinemetaJson(cached, isSeries = false)
                if (parsedCinemeta.isNotEmpty()) return@withContext parsedCinemeta
            } catch (_: Exception) {}
        }

        // Tier 4: Cinemeta Fallback (Global catalog)
        val cinemetaData = fetchFromNetwork(CINEMETA_MOVIES_URL)
        if (cinemetaData != null) {
            val parsed = parseCinemetaJson(cinemetaData, isSeries = false)
            if (parsed.isNotEmpty()) {
                try {
                    cacheFile.writeText(cinemetaData)
                } catch (_: Exception) {}
                return@withContext parsed
            }
        }

        // Tier 5: Curated Baseline
        getCuratedBaselineMovies()
    }

    suspend fun getTrendingSeries(forceRefresh: Boolean = false): List<MediaModel> = withContext(Dispatchers.IO) {
        val tmdbLang = getTmdbLanguage()
        val cacheFile = getSeriesCacheFile(tmdbLang)
        val tmdbUrl = "$TMDB_BASE_URL/trending/tv/week?api_key=$TMDB_API_KEY&language=$tmdbLang"

        // Tier 1: Instant load from disk cache if already cached for this language and not forcing refresh
        if (!forceRefresh && cacheFile.exists()) {
            try {
                val cached = cacheFile.readText()
                val parsedTmdb = parseTmdbJson(cached, isSeries = true)
                if (parsedTmdb.isNotEmpty()) return@withContext parsedTmdb
            } catch (_: Exception) {}
        }

        // Tier 2: TMDb API Network Fetch
        val tmdbData = fetchFromNetwork(tmdbUrl)
        if (tmdbData != null) {
            val parsed = parseTmdbJson(tmdbData, isSeries = true)
            if (parsed.isNotEmpty()) {
                try {
                    cacheFile.writeText(tmdbData)
                } catch (_: Exception) {}
                return@withContext parsed
            }
        }

        // Tier 3: Local Disk Cache Fallback (if network failed during forceRefresh)
        if (cacheFile.exists()) {
            try {
                val cached = cacheFile.readText()
                val parsedTmdb = parseTmdbJson(cached, isSeries = true)
                if (parsedTmdb.isNotEmpty()) return@withContext parsedTmdb
                val parsedCinemeta = parseCinemetaJson(cached, isSeries = true)
                if (parsedCinemeta.isNotEmpty()) return@withContext parsedCinemeta
            } catch (_: Exception) {}
        }

        // Tier 4: Cinemeta Fallback
        val cinemetaData = fetchFromNetwork(CINEMETA_SERIES_URL)
        if (cinemetaData != null) {
            val parsed = parseCinemetaJson(cinemetaData, isSeries = true)
            if (parsed.isNotEmpty()) {
                try {
                    cacheFile.writeText(cinemetaData)
                } catch (_: Exception) {}
                return@withContext parsed
            }
        }

        // Tier 5: Curated Baseline
        getCuratedBaselineSeries()
    }

    private fun fetchFromNetwork(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV; OrbitTV/1.0)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTmdbJson(jsonStr: String, isSeries: Boolean): List<MediaModel> {
        val result = mutableListOf<MediaModel>()
        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0).toString()
                if (id == "0") continue

                val title = if (isSeries) {
                    val name = item.optString("name", "").trim()
                    val orig = item.optString("original_name", "").trim()
                    if (name.isNotBlank()) name else if (orig.isNotBlank()) orig else "Untitled"
                } else {
                    val t = item.optString("title", "").trim()
                    val orig = item.optString("original_title", "").trim()
                    if (t.isNotBlank()) t else if (orig.isNotBlank()) orig else "Untitled"
                }

                val posterPath = item.optString("poster_path", "").trim()
                if (posterPath.isBlank() || posterPath == "null") continue
                val posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"

                val voteAvg = item.optDouble("vote_average", 0.0)
                val rating = if (voteAvg > 0.0) String.format(Locale.US, "%.1f", voteAvg) else ""
                val badge = if (rating.isNotBlank()) "★ $rating" else if (isSeries) context.getString(R.string.badge_series) else context.getString(R.string.badge_movie)

                val dateStr = if (isSeries) item.optString("first_air_date", "") else item.optString("release_date", "")
                val year = if (dateStr.length >= 4) dateStr.substring(0, 4) else ""

                val genreIds = item.optJSONArray("genre_ids")
                val genresList = mutableListOf<String>()
                if (genreIds != null) {
                    for (g in 0 until genreIds.length()) {
                        val gName = mapTmdbGenre(genreIds.optInt(g))
                        if (gName.isNotBlank()) genresList.add(gName)
                    }
                }
                val rawGenre = if (genresList.isNotEmpty()) genresList.joinToString(", ") else context.getString(R.string.genre_feature)
                val rawOverview = item.optString("overview", "").trim()
                val description = if (rawOverview.isNotBlank() && rawOverview != "null") {
                    rawOverview
                } else {
                    context.getString(R.string.media_desc_unavailable)
                }

                result.add(
                    MediaModel(
                        id = id,
                        title = title,
                        posterUrl = posterUrl,
                        badge = badge,
                        genre = rawGenre,
                        rawGenres = genresList,
                        rating = rating,
                        description = description,
                        releaseYear = year,
                        isSeries = isSeries,
                        deeplinkUri = if (isSeries) "https://www.themoviedb.org/tv/$id" else "https://www.themoviedb.org/movie/$id"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun mapTmdbGenre(id: Int): String {
        return when (id) {
            28, 10759 -> context.getString(R.string.genre_action)
            12 -> context.getString(R.string.genre_adventure)
            16 -> context.getString(R.string.genre_animation)
            35 -> context.getString(R.string.genre_comedy)
            80 -> context.getString(R.string.genre_crime)
            99 -> context.getString(R.string.genre_documentary)
            18 -> context.getString(R.string.genre_drama)
            10751 -> context.getString(R.string.genre_family)
            14, 10765 -> context.getString(R.string.genre_fantasy)
            36 -> context.getString(R.string.genre_history)
            27 -> context.getString(R.string.genre_horror)
            10402 -> context.getString(R.string.genre_music)
            9648 -> context.getString(R.string.genre_mystery)
            10749 -> context.getString(R.string.genre_romance)
            878 -> context.getString(R.string.genre_scifi)
            53 -> context.getString(R.string.genre_thriller)
            10752, 10768 -> context.getString(R.string.genre_war)
            37 -> context.getString(R.string.genre_western)
            else -> ""
        }
    }

    private fun parseCinemetaJson(jsonStr: String, isSeries: Boolean): List<MediaModel> {
        val result = mutableListOf<MediaModel>()
        try {
            val root = JSONObject(jsonStr)
            val metas = root.optJSONArray("metas") ?: return emptyList()

            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("id", "media_$i")
                val name = item.optString("name", "Untitled")
                val poster = item.optString("poster", "")
                if (poster.isBlank()) continue

                val genresArray = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        genresList.add(genresArray.optString(g))
                    }
                }
                val rawGenre = if (genresList.isNotEmpty()) translateGenres(genresList) else context.getString(R.string.genre_feature)

                val rating = item.optString("imdbRating", "")
                val badge = if (rating.isNotBlank()) "★ $rating" else if (isSeries) context.getString(R.string.badge_series) else context.getString(R.string.badge_movie)
                val rawDesc = item.optString("description", "")
                val finalDesc = if (getSystemLanguage() in listOf("ru", "be")) getRussianDescription(name, rawDesc) else rawDesc
                val year = item.optString("releaseInfo", "")

                result.add(
                    MediaModel(
                        id = id,
                        title = name,
                        posterUrl = poster,
                        badge = badge,
                        genre = rawGenre,
                        rawGenres = genresList,
                        rating = rating,
                        description = finalDesc,
                        releaseYear = year,
                        isSeries = isSeries,
                        deeplinkUri = "https://www.imdb.com/title/$id/"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun translateGenres(genres: List<String>): String {
        val genreMap = mapOf(
            "Action" to context.getString(R.string.genre_action),
            "Adventure" to context.getString(R.string.genre_adventure),
            "Animation" to context.getString(R.string.genre_animation),
            "Biography" to context.getString(R.string.genre_biography),
            "Comedy" to context.getString(R.string.genre_comedy),
            "Crime" to context.getString(R.string.genre_crime),
            "Documentary" to context.getString(R.string.genre_documentary),
            "Drama" to context.getString(R.string.genre_drama),
            "Family" to context.getString(R.string.genre_family),
            "Fantasy" to context.getString(R.string.genre_fantasy),
            "History" to context.getString(R.string.genre_history),
            "Horror" to context.getString(R.string.genre_horror),
            "Music" to context.getString(R.string.genre_music),
            "Musical" to context.getString(R.string.genre_musical),
            "Mystery" to context.getString(R.string.genre_mystery),
            "Romance" to context.getString(R.string.genre_romance),
            "Sci-Fi" to context.getString(R.string.genre_scifi),
            "Sport" to context.getString(R.string.genre_sport),
            "Thriller" to context.getString(R.string.genre_thriller),
            "War" to context.getString(R.string.genre_war),
            "Western" to context.getString(R.string.genre_western)
        )
        return genres.map { genreMap[it] ?: it }.joinToString(", ")
    }

    private fun getRussianDescription(title: String, originalDesc: String): String {
        val t = title.lowercase(Locale.ROOT)
        return when {
            t.contains("dexter") ->
                "Декстер Морган выходит из комы и отправляется в Нью-Йорк, полный решимости найти сына Гаррисона и всё исправить."
            t.contains("dune") ->
                "Пол Атрейдес объединяется с Чани и фрименами, чтобы отомстить заговорщикам, уничтожившим его семью."
            t.contains("oppenheimer") ->
                "История выдающегося физика Роберта Оппенгеймера, возглавившего Манхэттенский проект по созданию первой в мире атомной бомбы."
            t.contains("barbie") ->
                "Барби и Кен живут в идеальном беззаботном Барбиленде, пока внезапный кризис не вынуждает их отправиться в мир реальных людей."
            t.contains("house of the dragon") ->
                "Ожесточённая борьба за Железный трон внутри правящей династии Таргариенов, развязавшая гражданскую войну."
            t.contains("the last of us") ->
                "Спустя двадцать лет после краха человечества циничный выживший Джоэл соглашается тайно переправить 14-летнюю девочку Элли."
            t.contains("fallout") ->
                "Спустя 200 лет после ядерного апокалипсиса наивная жительница подземного бункера вынуждена выйти наружу в дикую Пустошь."
            t.contains("stranger things") ->
                "В тихом городке 1980-х загадочно исчезает мальчик, а его друзья натыкаются на правительственный заговор и монстров."
            else -> originalDesc.ifBlank { context.getString(R.string.media_desc_unavailable) }
        }
    }

    private fun getCuratedBaselineMovies(): List<MediaModel> {
        val lang = getEffectiveLanguage()
        val isUa = lang.startsWith("uk")
        val isBe = lang.startsWith("be")
        val isRu = lang in listOf("ru", "ru-ru", "kk")
        return listOf(
            MediaModel(
                id = "tt1160419",
                title = "Dune: Part Two",
                posterUrl = "https://images.metahub.space/poster/small/tt1160419/img",
                badge = "★ 8.6",
                genre = if (isUa) "Фантастика, Пригоди" else if (isBe) "Фантастыка, Прыгоды" else if (isRu) "Фантастика, Приключения" else "Sci-Fi, Adventure",
                rating = "8.6",
                description = when {
                    isUa -> "Пол Атрід об'єднується з Чані та фрименами, прагнучи помститися змовникам, які знищили його родину."
                    isBe -> "Пол Атрэйдэс аб'ядноўваецца з Чані і фрыменамі, каб адпомсціць змоўшчыкам, якія знішчылі яго сям'ю."
                    isRu -> "Пол Атрейдес объединяется с Чани и фрименами, чтобы отомстить заговорщикам, уничтожившим его семью."
                    else -> "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family."
                },
                releaseYear = "2024"
            ),
            MediaModel(
                id = "tt15398776",
                title = "Oppenheimer",
                posterUrl = "https://images.metahub.space/poster/small/tt15398776/img",
                badge = "★ 8.9",
                genre = if (isUa) "Біографія, Драма, Історія" else if (isBe) "Біяграфія, Драма, Гісторыя" else if (isRu) "Биография, Драма, История" else "Biography, Drama, History",
                rating = "8.9",
                description = when {
                    isUa -> "Історія видатного фізика Роберта Оппенгеймера, який очолив Мангеттенський проєкт зі створення першої атомної бомби."
                    isBe -> "Гісторыя выбітнага фізіка Роберта Опенгеймера, які ўзначаліў Манхэтэнскі праект па стварэнні першай атамнай бомбы."
                    isRu -> "История выдающегося физика Роберта Оппенгеймера, возглавившего Манхэттенский проект по созданию первой в мире атомной бомбы."
                    else -> "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb."
                },
                releaseYear = "2023"
            ),
            MediaModel(
                id = "tt1517268",
                title = "Barbie",
                posterUrl = "https://images.metahub.space/poster/small/tt1517268/img",
                badge = "★ 7.0",
                genre = if (isUa) "Пригоди, Комедія" else if (isBe) "Прыгоды, Камедыя" else if (isRu) "Приключения, Комедия" else "Adventure, Comedy",
                rating = "7.0",
                description = when {
                    isUa -> "Барбі та Кен живуть в ідеальному світі Барбіленду, поки криза не змушує їх вирушити у світ реальних людей."
                    isBe -> "Барбі і Кен жывуць у ідэальным свеце Барбілэнда, пакуль крызіс не вымушае іх выправіцца ў свет рэальных людзей."
                    isRu -> "Барби и Кен живут в идеальном беззаботном Барбиленде, пока внезапный кризис не вынуждает их отправиться в мир реальных людей."
                    else -> "Barbie and Ken are having the time of their lives in the colorful and seemingly perfect world of Barbie Land."
                },
                releaseYear = "2023"
            ),
            MediaModel(
                id = "tt9362722",
                title = "Spider-Man: Across the Spider-Verse",
                posterUrl = "https://images.metahub.space/poster/small/tt9362722/img",
                badge = "★ 8.7",
                genre = if (isUa) "Анімація, Бойовик" else if (isBe) "Мультфільм, Баявік" else if (isRu) "Мультфильм, Боевик" else "Animation, Action",
                rating = "8.7",
                description = when {
                    isUa -> "Майлз Моралес вирушає у подорож крізь Мультивсесвіт, де зустрічає команду Людей-павуків."
                    isBe -> "Майлз Маралес выпраўляецца ў падарожжа праз Мульцісусвет, дзе сустракае каманду Людзей-павукоў."
                    isRu -> "Майлз Моралес отправляется в путешествие сквозь Мультивселенную, где встречает команду Людей-павуков."
                    else -> "Miles Morales catapults across the Multiverse, encountering a team of Spider-People charged with protecting its existence."
                },
                releaseYear = "2023"
            )
        )
    }

    private fun getCuratedBaselineSeries(): List<MediaModel> {
        val lang = getEffectiveLanguage()
        val isUa = lang.startsWith("uk")
        val isBe = lang.startsWith("be")
        val isRu = lang in listOf("ru", "ru-ru", "kk")
        return listOf(
            MediaModel(
                id = "tt11198330",
                title = "House of the Dragon",
                posterUrl = "https://images.metahub.space/poster/small/tt11198330/img",
                badge = "★ 8.4",
                genre = if (isUa) "Бойовик, Пригоди, Драма" else if (isBe) "Баявік, Прыгоды, Драма" else if (isRu) "Боевик, Приключения, Драма" else "Action, Adventure, Drama",
                rating = "8.4",
                description = when {
                    isUa -> "Запекла боротьба за Залізний трон всередині правлячої династії Таргарієнів, що розпалила громадянську війну."
                    isBe -> "Зацятая барацьба за Жалезны трон унутры дынастыі Таргарыенаў, якая развязала грамадзянскую вайну."
                    isRu -> "Ожесточённая борьба за Железный трон внутри правящей династии Таргариенов, развязавшая разрушительную гражданскую войну."
                    else -> "An internal succession contest within House Targaryen at the height of its power."
                },
                releaseYear = "2022",
                isSeries = true
            ),
            MediaModel(
                id = "tt3581920",
                title = "The Last of Us",
                posterUrl = "https://images.metahub.space/poster/small/tt3581920/img",
                badge = "★ 8.7",
                genre = if (isUa) "Бойовик, Пригоди, Драма" else if (isBe) "Баявік, Прыгоды, Драма" else if (isRu) "Боевик, Приключения, Драма" else "Action, Adventure, Drama",
                rating = "8.7",
                description = when {
                    isUa -> "Через двадцять років після краху цивілізації загартований Джоел погоджується таємно переправити 14-річну дівчинку Еллі."
                    isBe -> "Праз дваццаць гадоў пасля краху цывілізацыі загартаваны Джоэл згаджаецца таемна пераправіць 14-гадовую дзяўчынку Элі."
                    isRu -> "Спустя двадцать лет после краха человечества циничный выживший Джоэл соглашается тайно переправить 14-летнюю девочку Элли."
                    else -> "After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl."
                },
                releaseYear = "2023",
                isSeries = true
            ),
            MediaModel(
                id = "tt12637874",
                title = "Fallout",
                posterUrl = "https://images.metahub.space/poster/small/tt12637874/img",
                badge = "★ 8.4",
                genre = if (isUa) "Бойовик, Пригоди, Фантастика" else if (isBe) "Баявік, Прыгоды, Фантастыка" else if (isRu) "Боевик, Приключения, Фантастика" else "Action, Adventure, Sci-Fi",
                rating = "8.4",
                description = when {
                    isUa -> "Через 200 років після ядерного апокаліпсису наївна жителька підземного притулку змушена вийти на поверхню в жорстоку Пустош."
                    isBe -> "Праз 200 гадоў пасля ядзернага апакаліпсісу наіўная жыхарка падземнага бункера вымушана выйсці на бязлітасную Пустку."
                    isRu -> "Спустя 200 лет после ядерного апокалипсиса наивная жительница подземного бункера вынуждена выйти наружу в дикую Пустошь."
                    else -> "In a future, post-apocalyptic Los Angeles brought about by nuclear decimation, citizens must live in underground bunkers."
                },
                releaseYear = "2024",
                isSeries = true
            ),
            MediaModel(
                id = "tt4574334",
                title = "Stranger Things",
                posterUrl = "https://images.metahub.space/poster/small/tt4574334/img",
                badge = "★ 8.7",
                genre = if (isUa) "Драма, Фентезі, Жахи" else if (isBe) "Драма, Фэнтэзі, Жахі" else if (isRu) "Драма, Фэнтези, Ужасы" else "Drama, Fantasy, Horror",
                rating = "8.7",
                description = when {
                    isUa -> "У тихому містечку 1980-х таємниче зникає хлопчик, а його друзі стикаються з урядовою змовою та монстрами."
                    isBe -> "У ціхім мястэчку 1980-х таямніча знікае хлопчык, а яго сябры сутыкаюцца з урадавай змовай і монстрамі."
                    isRu -> "В тихом городке 1980-х загадочно исчезает мальчик, а его друзья натыкаются на правительственный заговор и монстров."
                    else -> "When a young boy vanishes, a small town uncovers a mystery involving secret experiments and supernatural forces."
                },
                releaseYear = "2016",
                isSeries = true
            )
        )
    }

    companion object {
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val TMDB_API_KEY = "2c46288716a18fb7aadcc2a801f3fc6b"
        private const val CINEMETA_MOVIES_URL = "https://v3-cinemeta.strem.io/catalog/movie/top.json"
        private const val CINEMETA_SERIES_URL = "https://v3-cinemeta.strem.io/catalog/series/top.json"
    }
}
