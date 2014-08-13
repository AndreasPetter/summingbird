/*
 Copyright 2014 Twitter, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.twitter.summingbird.graph

/**
 * This is a weak heterogenous map. It uses equals on the keys,
 * to it is your responsibilty that if k: K[_] == k2: K[_] then
 * the types are actually equal (either be careful or store a
 * type identifier.
 */
trait HMap[K[_], V[_]] {
  type Pair[t] = (K[t], V[t])
  protected val map: Map[K[_], V[_]]
  override def toString: String =
    "H%s".format(map)

  def +[T](kv: (K[T], V[T])): HMap[K, V] = {
    val self = this
    new HMap[K, V] {
      override protected val map = self.map + kv
    }
  }
  def -[T](k: K[T]): HMap[K, V] = {
    val self = this
    new HMap[K, V] {
      override protected val map = self.map - k
    }
  }
  def apply[T](id: K[T]): V[T] = get(id).get
  def get[T](id: K[T]): Option[V[T]] =
    map.get(id).asInstanceOf[Option[V[T]]]

  def keysOf[T](v: V[T]): Set[K[T]] = map.collect {
    case (k, w) if v == w =>
      k.asInstanceOf[K[T]]
  }.toSet

  // go through all the keys, and find the first key that matches this
  // function and apply
  def updateFirst(p: GenPartial[K, V]): Option[(HMap[K, V], K[_])] = {
    def collector[T]: PartialFunction[(K[T], V[T]), (K[T], V[T])] = {
      val pf = p.apply[T]

      {
        case (kv: (K[T], V[T])) if pf.isDefinedAt(kv._1) =>
          val v2 = pf(kv._1)
          (kv._1, v2)
      }
    }

    map.asInstanceOf[Map[K[Any], V[Any]]].collectFirst(collector)
      .map { kv =>
        (this + kv, kv._1)
      }
  }

  def collect[R[_]](p: GenPartial[Pair, R]): Iterable[R[_]] =
    map.asInstanceOf[Iterable[(K[Any], V[Any])]].collect(p.apply)

  def collectValues[R[_]](p: GenPartial[V, R]): Iterable[R[_]] =
    map.values.asInstanceOf[Iterable[V[Any]]].collect(p.apply)
}

// This is a function that preserves the inner type
trait GenFunction[T[_], R[_]] {
  def apply[U]: (T[U] => R[U])
}

trait GenPartial[T[_], R[_]] {
  def apply[U]: PartialFunction[T[U], R[U]]
}

object HMap {
  def empty[K[_], V[_]]: HMap[K, V] = new HMap[K, V] { override val map = Map.empty[K[_], V[_]] }
}

/**
 * This is a useful cache for memoizing heterogenously types functions
 */
class HCache[K[_], V[_]]() {
  private var hmap: HMap[K, V] = HMap.empty[K, V]

  /**
   * Get snapshot of the current state
   */
  def snapshot: HMap[K, V] = hmap

  def getOrElseUpdate[T](k: K[T], v: => V[T]): V[T] =
    hmap.get(k) match {
      case Some(exists) => exists
      case None =>
        val res = v
        hmap = hmap + (k -> res)
        res
    }
}
