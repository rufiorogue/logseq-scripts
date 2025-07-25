(ns generate-whiteboard-from-image-dir
    (:require   [clojure.edn :as edn]
                [clojure.java.io :as io]
                [clojure.pprint :refer [pprint]]
                [clojure.tools.cli :refer [parse-opts]])
    (:import [java.util UUID]))

(defn uuid [] (UUID/randomUUID))

(defn timestamp [] (System/currentTimeMillis))

(defn load-whiteboard [path]
    (-> path slurp edn/read-string))

(defn save-whiteboard [path data]
    (with-open [w (io/writer path)]
        (binding [*print-namespace-maps* false]
            (pprint data w))))

; calculate path of b as relative to a
(defn relative-path [a b]
    (let [  path-a (.toPath (io/file a))
            path-b (.toPath (io/file b))
            can-relativize? (if (.getRoot path-a)
                                (some? (.getRoot path-b))
                                (not (.getRoot path-b)))]
    (when can-relativize?
        (str (.relativize path-a path-b)))))


(defn image-block [whiteboard-path img-fn image-uuid parent-uuid ]
    {   :block/content (str "![](" (relative-path (.getParent (io/file whiteboard-path)) img-fn) ")")
        :block/format :markdown
        :block/uuid image-uuid
        :block/left {:block-uuid parent-uuid}
        :block/parent {:block-uuid parent-uuid}})

(defn shape-block [shape-uuid image-uuid parent-uuid idx x y]
    (let [ts (timestamp)]
        { :block/created-at ts
            :block/updated-at ts
            :block/properties
            { :ls-type :whiteboard-shape
                :logseq.tldraw.shape
                {   :type "logseq-portal"
                    :id (str shape-uuid)
                    :pageId (str image-uuid)
                    :parentId (str parent-uuid)
                    :nonce ts
                    :index idx
                    :point [x y]
                    :size [320 240]
                    :scale [1 1]
                    :scaleLevel "xs"
                    :opacity 1
                    :collapsed false
                    :collapsedHeight 0
                    :compact true
                    :isAutoResizing true
                    :noFill false
                    :fill ""
                    :stroke ""
                    :strokeType "line"
                    :strokeWidth 2
                    :borderRadius 8
                    :blockType "B"}}}))


(def supported-formats-extensions
  #{"jpg" "jpeg" "png" "gif" "bmp" "webp" "mp4" "webm" "mov" "avi" "mkv"})


(defn is-supported-format? [filename ext-set]
  (some-> filename
          clojure.string/lower-case
          (clojure.string/split #"\.")
          last
          ext-set))

(defn run  [{:keys [whiteboard-file image-dir]}]
    (let [  images (->> (file-seq (io/file image-dir))
                        (filter #(.isFile %))
                        (filter #(is-supported-format? (.getName %) supported-formats-extensions))
                        (map #(.getPath %))
                        (sort)
                        )
            initial (load-whiteboard whiteboard-file)
            blocks (:blocks initial)
            page-uuid (:block/uuid (first (:pages initial)))
            [x0 y0] [100 100]
            spacing 400
            new-blocks (flatten(map-indexed
                        (fn [idx img]
                            (let [  image-uuid (uuid)
                                    shape-uuid (uuid)
                                    x (+ x0 (* (mod idx 5) spacing))
                                    y (+ y0 (* (quot idx 5) spacing)) ]
                                [(image-block whiteboard-file img image-uuid page-uuid)
                                (shape-block shape-uuid image-uuid page-uuid idx x y)]))
                        images))
            new-blocks-shape-uuids (->> new-blocks
                                        (filter #(= :whiteboard-shape (get-in % [:block/properties :ls-type])))
                                        (map #(get-in % [:block/properties :logseq.tldraw.shape :id])))
        ]

    (save-whiteboard whiteboard-file
                    (as-> initial s
                        (assoc s :blocks (concat blocks (flatten new-blocks)))
                        (assoc s :pages (list (update-in (first (:pages s))
                                                    [:block/properties :logseq.tldraw.page :shapes-index]
                                                    #(concat % new-blocks-shape-uuids))) )))
    (println "Inserted" (count images) "images.")
    ))

(def cli-options
  [["-w" "--whiteboard-file PATH" "Path to whiteboard file"
    :parse-fn str
    :validate [#(.exists (clojure.java.io/file %)) "File doesn't exist"]]

   ["-i" "--image-dir PATH" "Path to image directory"
    :parse-fn str
    :validate [#(.isDirectory (clojure.java.io/file %)) "Not a directory"]]

   ["-h" "--help"]])

(defn -main [& args]
    (let [{:keys [options errors summary]} (parse-opts args cli-options)
        {:keys [whiteboard-file image-dir]} options]
    (cond
        (:help options)
        (do
            (println "Usage:")
            (println summary))

        (seq errors)
        (do
            (doseq [err errors] (println "Error:" err))
            (println "Usage:")
            (println summary))

        (or (nil? whiteboard-file) (nil? image-dir))
        (println "Missing required arguments: --whiteboard-file and/or --image-dir")

        :else
        (do
            (println "Whiteboard:" whiteboard-file)
            (println "Images:" image-dir)
            (run {:whiteboard-file whiteboard-file
                :image-dir image-dir})))))