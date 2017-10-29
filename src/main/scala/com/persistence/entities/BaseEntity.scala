package com.persistence.entities

/**
  * Created by SeungEun on 2016/11/05.
  */
trait BaseEntity {
    val id: Long
    
    def isValid: Boolean = true
}
