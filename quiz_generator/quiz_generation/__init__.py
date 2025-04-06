from quiz_generator.quiz_generation.generator import agenerate_quiz

# TODO: Workaround for pytube issue, langchain-community==0.3.18 uses pytube to fetch additional video info, but the module is not maintained anymore, and we need langchain to use a proxy for youtube requests
import sys
import requests
from youtube_transcript_api import YouTubeTranscriptApi
import os
from pytubefix import YouTube as FixedYouTube

import logging

logger = logging.getLogger(__name__)

# Define proxy settings
default_proxies = {
    "http": os.getenv("PROXY_URL"),
    "https": os.getenv("PROXY_URL"),
}

# Monkey patch for youtube_transcript_api
original_list_transcripts = YouTubeTranscriptApi.list_transcripts


def patched_list_transcripts(cls, video_id, proxies=None, cookies=None):
    # Use the default proxies if none are provided and PROXY_URL is set
    if proxies is None:
        proxies = default_proxies
    logger.info(f"Using proxies in transcript list: {proxies}")
    return original_list_transcripts(
        video_id=video_id, proxies=proxies, cookies=cookies
    )


YouTubeTranscriptApi.list_transcripts = classmethod(patched_list_transcripts)


# Monkey patch for pytubefix
class YouTubeProxy(FixedYouTube):
    def __init__(self, *args, **kwargs):
        self.session = requests.Session()
        self.session.proxies.update(default_proxies)
        logger.info(f"Using proxies in pytubefix: {self.session.proxies}")
        super().__init__(*args, **kwargs)


# Apply the monkey patch
import pytube

sys.modules["pytube"].YouTube = YouTubeProxy
