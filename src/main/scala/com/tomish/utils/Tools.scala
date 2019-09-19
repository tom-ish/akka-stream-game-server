package com.tomish.utils

import java.awt.Color

import com.tomish.GameArea
import com.tomish.actor.Position

import scala.util.Random

object Tools {

  def getRandomColor = {
    val r = new Random()
    "#"+ r.nextInt(256).toHexString + r.nextInt(256).toHexString + r.nextInt(256).toHexString
  }

  def getColor(color: Color) = {
    "#" + color.getRed.toHexString + color.getGreen.toHexString + color.getBlue.toHexString
  }

  def getRandomPosition = {
    val r = new Random()
    val x = r.nextInt(GameArea.width)
    val y = r.nextInt(GameArea.height)
    Position(x, y)
  }
}
