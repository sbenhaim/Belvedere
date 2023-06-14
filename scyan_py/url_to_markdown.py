#!/usr/bin/env python3

import requests
from bs4 import BeautifulSoup
import html2text

def url_to_markdown(url):
    # Fetch the content from the URL
    response = requests.get(url)
    response.raise_for_status()

    # Parse the HTML content using BeautifulSoup
    soup = BeautifulSoup(response.text, 'html.parser')

    # Convert HTML to markdown using html2text
    markdown_converter = html2text.HTML2Text()
    markdown_converter.ignore_links = False
    markdown_converter.ignore_images = True
    markdown = markdown_converter.handle(str(soup))

    return markdown
