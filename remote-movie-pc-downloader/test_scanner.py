import os
import sys
import shutil
import tempfile
import asyncio
import unittest

from library_scanner import (
    is_trickplay_or_ignored,
    is_video_file,
    is_adult_by_heuristics,
    get_unique_destination_path,
    move_media_item,
    collect_library_items,
    scan_and_organize_libraries
)

class TestLibraryScanner(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix="media_test_")
        self.movies_dir = os.path.join(self.temp_dir, "movies")
        self.adult_dir = os.path.join(self.temp_dir, "adult")
        os.makedirs(self.movies_dir, exist_ok=True)
        os.makedirs(self.adult_dir, exist_ok=True)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_trickplay_and_image_ignoring(self):
        # Trickplay files/folders
        self.assertTrue(is_trickplay_or_ignored("/movies/Inception/trickplay/0.bif"))
        self.assertTrue(is_trickplay_or_ignored("C:\\movies\\.trickplay\\tile.bif"))
        self.assertTrue(is_trickplay_or_ignored("C:\\movies\\metadata\\fanart.jpg"))
        self.assertTrue(is_trickplay_or_ignored("C:\\movies\\extrathumbs\\thumb.jpg"))
        
        # Regular video should not be ignored
        self.assertFalse(is_trickplay_or_ignored("C:\\movies\\Inception.2010.1080p.mkv"))
        
        # is_video_file checks
        self.assertTrue(is_video_file("C:\\movies\\Inception.mkv"))
        self.assertTrue(is_video_file("C:\\movies\\video.mp4"))
        self.assertFalse(is_video_file("C:\\movies\\poster.jpg"))
        self.assertFalse(is_video_file("C:\\movies\\subtitle.srt"))
        self.assertFalse(is_video_file("C:\\movies\\trickplay\\0.bif"))

    def test_adult_heuristics_detection(self):
        # Studios
        adult_samples = [
            "Brazzers - Ex-Girlfriend Revenge 1080p.mp4",
            "NaughtyAmerica - Nicole Aniston (2021).mkv",
            "RealityKings.21.05.14.Abella.Danger.mp4",
            "Blacked - Kendra Lust 4K.mp4",
            "Vixen - Eva Elfie Seduction.mkv",
            "FakeTaxi.23.01.12.mp4",
            "Wicked.Pictures.Feature.mkv",
            "Private.Gold.720p.mp4",
            "MomComesFirst - Cory Chase - Lesson (2022) 1080p.mp4",
            "SisLovesMe - Lauren Phillips - Seduction.mp4",
            "StepMomLessons - Reagan Foxx (2021).mkv",
            "DaughterSwap - Kenzie Reeves - Taboo.mp4",
            "DadCrush - Gabbie Carter.mp4",
            "ShopLyfter - Scene 04.mp4",
            "PureTaboo - Secret Family.mp4",
            "Tiny4K - Maya Kendrick.mkv"
        ]
        for sample in adult_samples:
            is_adult, reason = is_adult_by_heuristics(sample)
            self.assertTrue(is_adult, f"Expected adult detection for '{sample}', reason: {reason}")

        # JAV Code patterns
        jav_samples = [
            "SSIS-099.mp4",
            "IPX-534.1080p.mkv",
            "MIDE-890.avi",
            "FC2-PPV-1234567.mp4",
            "HEYZO-2345.mp4",
            "1pondo-010121_001.mp4"
        ]
        for sample in jav_samples:
            is_adult, reason = is_adult_by_heuristics(sample)
            self.assertTrue(is_adult, f"Expected JAV adult detection for '{sample}', reason: {reason}")

        # Explicit Keywords + xxx-suffix studio names
        keyword_samples = [
            "Random_Video_18+_Uncensored.mkv",
            "Leaked_XXX_Hardcore_Tape.mp4",
            "Hentai_Episode_01_1080p.mp4",
            # xxx as suffix — the main regression case
            "FamilyTherapyXXX - Ashley Alexander - Natural (29.04.2026) 1080p rq.mp4",
            "MomXXX - Scene 01.mp4",
            "WankzVR.Scene.mkv",
            "xxxProposal.2022.mp4",
        ]
        for sample in keyword_samples:
            is_adult, reason = is_adult_by_heuristics(sample)
            self.assertTrue(is_adult, f"Expected adult keyword detection for '{sample}', reason: {reason}")

        # Mainstream Movies (should NOT be detected as adult)
        mainstream_samples = [
            "3 Idiots 2009 720p 10bit Bluray HIN AAC5.1 x265 HEVC ESu.mkv",
            "Inception.2010.1080p.BluRay.x264.mkv",
            "The Dark Knight (2008) 2160p UHD.mkv",
            "Hotel Transylvania (2012) 720p.mp4",
            "Spider-Man.No.Way.Home.2021.mkv",
            "Interstellar.2014.Remux.mkv",
            "Breaking Bad S01E02 1080p.mkv",
            "Avatar 2009 1080p BluRay.mkv",
            "Iron Man 2008 1080p.mkv",
            "xXx (2002) 1080p BluRay.mkv",
            "xXx Return of Xander Cage (2017) 1080p.mkv",
            "Saw 2004 1080p.mkv",
            "Cars 2006 1080p.mkv",
            "Dune 2021 2160p.mkv",
            "Cast Away (2000) 1080p BluRay.mkv",
        ]
        for sample in mainstream_samples:
            is_adult, reason = is_adult_by_heuristics(sample)
            self.assertFalse(is_adult, f"Mainstream movie falsely detected as adult: '{sample}' ({reason})")

    def test_unique_destination_path(self):
        dest_file = os.path.join(self.temp_dir, "test.mp4")
        with open(dest_file, "w") as f:
            f.write("content 1")

        unique_path = get_unique_destination_path(dest_file)
        self.assertEqual(unique_path, os.path.join(self.temp_dir, "test (1).mp4"))

        # Create the (1) version too
        with open(unique_path, "w") as f:
            f.write("content 2")

        unique_path_2 = get_unique_destination_path(dest_file)
        self.assertEqual(unique_path_2, os.path.join(self.temp_dir, "test (2).mp4"))

    def test_move_subfolder_and_loose_files(self):
        # 1. Create a dedicated adult movie subfolder in movies_dir
        adult_folder = os.path.join(self.movies_dir, "Brazzers - Scene Collection")
        os.makedirs(adult_folder, exist_ok=True)
        with open(os.path.join(adult_folder, "scene.mp4"), "w") as f:
            f.write("video")
        with open(os.path.join(adult_folder, "scene.srt"), "w") as f:
            f.write("sub")
        with open(os.path.join(adult_folder, "poster.jpg"), "w") as f:
            f.write("image")

        # 2. Create a loose adult video with subtitle in movies_dir
        loose_adult = os.path.join(self.movies_dir, "SSIS-123.mp4")
        loose_sub = os.path.join(self.movies_dir, "SSIS-123.srt")
        with open(loose_adult, "w") as f:
            f.write("video")
        with open(loose_sub, "w") as f:
            f.write("sub")

        # 3. Create a normal mainstream movie in movies_dir
        normal_movie = os.path.join(self.movies_dir, "Inception (2010).mkv")
        with open(normal_movie, "w") as f:
            f.write("movie")

        # 4. Create trickplay files inside movies_dir (should NOT trigger as video or move independently)
        trickplay_dir = os.path.join(self.movies_dir, "trickplay")
        os.makedirs(trickplay_dir, exist_ok=True)
        with open(os.path.join(trickplay_dir, "0.bif"), "w") as f:
            f.write("bif")

        # Run the scan
        results = asyncio.run(scan_and_organize_libraries(self.movies_dir, self.adult_dir))

        # Assertions
        # 1. Adult folder should be moved to adult_dir
        self.assertFalse(os.path.exists(adult_folder))
        self.assertTrue(os.path.exists(os.path.join(self.adult_dir, "Brazzers - Scene Collection")))
        self.assertTrue(os.path.exists(os.path.join(self.adult_dir, "Brazzers - Scene Collection", "scene.srt")))

        # 2. Loose adult file + srt should be moved to adult_dir
        self.assertFalse(os.path.exists(loose_adult))
        self.assertFalse(os.path.exists(loose_sub))
        self.assertTrue(os.path.exists(os.path.join(self.adult_dir, "SSIS-123.mp4")))
        self.assertTrue(os.path.exists(os.path.join(self.adult_dir, "SSIS-123.srt")))

        # 3. Normal movie should stay in movies_dir
        self.assertTrue(os.path.exists(normal_movie))

        # 4. Results counts
        self.assertEqual(len(results["moved_to_adult"]), 2)
        self.assertEqual(len(results["errors"]), 0)

    def test_restore_mainstream_movies_from_adult_folder(self):
        # Place 3 Idiots and Cast Away inside adult_dir
        idiots_movie = os.path.join(self.adult_dir, "3 Idiots 2009 720p 10bit Bluray HIN AAC5.1 x265 HEVC ESu.mkv")
        with open(idiots_movie, "w") as f:
            f.write("idiots content")

        castaway_folder = os.path.join(self.adult_dir, "Cast Away (2000)")
        os.makedirs(castaway_folder, exist_ok=True)
        with open(os.path.join(castaway_folder, "Cast.Away.2000.1080p.mkv"), "w") as f:
            f.write("cast away content")
        with open(os.path.join(castaway_folder, "Cast.Away.2000.srt"), "w") as f:
            f.write("sub content")

        # Also place a real adult video in adult_dir
        real_adult = os.path.join(self.adult_dir, "Brazzers - Scene.mp4")
        with open(real_adult, "w") as f:
            f.write("adult content")

        # Run scan (without TMDB key)
        results = asyncio.run(scan_and_organize_libraries(self.movies_dir, self.adult_dir, tmdb_api_key=None))

        # Real adult movie stays in adult_dir
        self.assertTrue(os.path.exists(real_adult))

        # 3 Idiots and Cast Away must be moved back to movies_dir
        self.assertFalse(os.path.exists(idiots_movie))
        self.assertTrue(os.path.exists(os.path.join(self.movies_dir, "3 Idiots 2009 720p 10bit Bluray HIN AAC5.1 x265 HEVC ESu.mkv")))

        self.assertFalse(os.path.exists(castaway_folder))
        self.assertTrue(os.path.exists(os.path.join(self.movies_dir, "Cast Away (2000)")))
        self.assertTrue(os.path.exists(os.path.join(self.movies_dir, "Cast Away (2000)", "Cast.Away.2000.srt")))

        self.assertEqual(len(results["moved_to_movies"]), 2)
        self.assertEqual(len(results["errors"]), 0)


if __name__ == "__main__":
    unittest.main()

