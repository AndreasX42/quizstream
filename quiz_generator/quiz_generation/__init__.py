from quiz_generator.quiz_generation.generator import agenerate_quiz

# TODO: Workaround for pytube issue, langchain-community==0.3.18 uses pytube to fetch additinal video info, but the module is not maintained anymore.
import sys
from pytubefix import YouTube as FixedYouTube
import pytube

sys.modules["pytube"].YouTube = FixedYouTube
