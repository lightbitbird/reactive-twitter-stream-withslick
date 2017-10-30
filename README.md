# Reactive Twitter Stream With Slick
=========================

#
Akka actor gets tweets from Twitter Streaming API and chunk them into words, register them in DB.
https://user-images.githubusercontent.com/14951865/32171589-ff5739ee-bdbb-11e7-98c9-7bdff03ae087.JPG

#
Akka actor sends SQS Message queues using Akka Actor daemon as a background process.
SQS ids are managed by other actor on database, the actor collects words each by prefecture in the score table with Akka Streams.
These process are just for testing SQS, Akka streams, actor daemon.
https://user-images.githubusercontent.com/14951865/32171592-002a091e-bdbc-11e7-8115-e9397ae110a4.JPG

#
Registerd words are pointed at by a prefecture on Google Map. You can see them the angular frontend page.
https://user-images.githubusercontent.com/14951865/32171595-01f6d1e6-bdbc-11e7-8daa-f5679d8b35f2.JPG

#
Multiple main classes detected, select one to run:

 [1] com.twitter.TwitterStreamer
     Akka actor gets tweets from Twitter Streaming API and chunk them into words, register them in DB.
 [2] com.resident.MessageHandler
     Akka actor sends SQS Message queues using Akka Actor daemon as a background process.
 [3] com.restapi.TwitterWordsApplication
     Registerd words are pointed at by a prefecture on Google Map.


# The Frontend Google Map Application
You can get the frontend application from the following repository
https://github.com/lightbitbird/twitter-words-map
