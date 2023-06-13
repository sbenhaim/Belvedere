(ns Scyan.load
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]))


;; Load the Python module langchain
(require-python '[langchain.document_loaders :as dl])
(require-python '[langchain.text_splitter :as ts])


(defn splitter [docs & {:keys [chunk-size chunk-overlap]
                        :or {chunk-size 1000
                             chunk-overlap 50}}]
  (ts/RecursiveCharacterTextSplitter :chunk_size chunk-size
                                     :chunk_overlap chunk-overlap))


(defn load-directory
  "Loads files from a specified directory using a DirectoryLoader.

  Parameters:
  - dir: A string representing the path to the directory from which files are to be loaded.

  Optional Keyword Arguments:
  - :glob (default \"**/*\"): A string representing a glob pattern used to match files within the directory.
    The default value \"**/*\" matches all files and directories recursively.
  - :loader-class (default dl/UnstructuredFileLoader): A class representing the type of loader to be used for loading files.
    The default value is dl/UnstructuredFileLoader, which is a loader class for unstructured files.

  Returns:
  - loaded documents."
  [dir & {:keys [glob loader-class chunk-size chunk-overlap]
          :or {glob "**/*"
               loader-class dl/UnstructuredFileLoader
               chunk-size 1000
               chunk-overlap 20}}]
  (let [loader (dl/DirectoryLoader dir :glob glob :loader_cls loader-class)]
    (py. loader load_and_split (splitter chunk-size chunk-overlap))))


(defn load-md
  [path]
  (let [loader (dl/UnstructuredMarkdownLoader path)
        splitter (ts/MarkdownTextSplitter :chunk_size 500 :chunk_overlap 20)]
    (py. loader load_and_split splitter)))


(comment
  (def docs
    (load-directory "../docs/databricks"
                    :glob "**/*.html"
                    :loader-class dl/BSHTMLLoader
                    :chunk-size 500
                    :chunk-overlap 10))

  (py/dir (first docs))

  (let [doc (first docs)]
    (py.- doc schema))

  (py.- (first docs) page_content)

  (tap> nodes)

  (py.- (first docs) metadata))
