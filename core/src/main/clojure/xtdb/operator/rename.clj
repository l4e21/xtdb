(ns xtdb.operator.rename
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [xtdb.logical-plan :as lp]
            [xtdb.operator.scan :as scan]
            [xtdb.util :as util]
            [xtdb.vector.reader :as vr])
  (:import (java.util LinkedList Map)
           java.util.function.Consumer
           xtdb.ICursor
           (xtdb.vector IVectorReader RelationReader)))

(defmethod lp/ra-expr :rename [_]
  (s/cat :op #{:ρ :rho :rename}
         :prefix (s/? ::lp/relation)
         :columns (s/? (s/map-of ::lp/column ::lp/column :conform-keys true))
         :relation ::lp/ra-expression))

(set! *unchecked-math* :warn-on-boxed)

(deftype RenameCursor [^ICursor in-cursor
                       ^Map #_#_<Symbol, Symbol> col-name-mapping
                       ^Map #_#_<Symbol, Symbol> col-name-reverse-mapping]
  ICursor
  (tryAdvance [_ c]
    (binding [scan/*column->pushdown-bloom* (->> (for [[k v] scan/*column->pushdown-bloom*]
                                                   [(get col-name-reverse-mapping k) v])
                                                 (into {}))]
      (.tryAdvance in-cursor
                   (reify Consumer
                     (accept [_ in-rel]
                       (let [^RelationReader in-rel in-rel
                             out-cols (LinkedList.)]

                         (doseq [^IVectorReader in-col in-rel
                                 :let [col-name (str (get col-name-mapping (symbol (.getName in-col))))]]
                           (.add out-cols (.withName in-col col-name)))

                         (.accept c (vr/rel-reader out-cols (.rowCount in-rel)))))))))

  (close [_]
    (util/try-close in-cursor)))

(defmethod lp/emit-expr :rename [{:keys [columns relation prefix]} args]
  (let [emitted-child-relation (lp/emit-expr relation args)]
    (lp/unary-expr emitted-child-relation
      (fn [fields]
        (let [col-name-mapping (->> (for [old-name (set (keys fields))]
                                      [old-name
                                       (cond-> (get columns old-name old-name)
                                         prefix (->> name (symbol (name prefix))))])
                                    (into {}))]
          {:fields (->> fields
                        (into {}
                              (map (juxt (comp col-name-mapping key) val))))
           :stats (:stats emitted-child-relation)
           :->cursor (fn [_opts in-cursor]
                       (RenameCursor. in-cursor col-name-mapping (set/map-invert col-name-mapping)))})))))
