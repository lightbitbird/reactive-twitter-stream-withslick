package com.resident

import com.tweet.daemon.MessageRegisterDaemon

object MessageHandler extends App {
  new MessageRegisterDaemon("dictionaryEngine").start
//  new ApplicationDaemon("dictionaryEngine").start
}
