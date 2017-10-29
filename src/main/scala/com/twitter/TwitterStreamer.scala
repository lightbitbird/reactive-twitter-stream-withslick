package com.twitter

import com.twitter.daemon.TwitterStreamingDaemon

object TwitterStreamer extends App {
  new TwitterStreamingDaemon("twitterDaemon").start()
}