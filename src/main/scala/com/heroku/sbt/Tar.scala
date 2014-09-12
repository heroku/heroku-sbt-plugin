package com.heroku.sbt

import java.io._
import org.apache.commons.compress.archivers.tar.{TarArchiveOutputStream, TarArchiveEntry}
import org.apache.commons.compress.utils.IOUtils
import sbt._

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.archivers.ArchiveOutputStream

object Tar {
  def create(filename: String, directory: String, outputDir: File): File = {
    if (useNativeTar) {
      val gzipFilename = filename + ".tgz"
      sbt.Process(Seq("tar", "pczf", gzipFilename, directory), outputDir).!!
      outputDir / gzipFilename
    } else {
      Pack(filename, outputDir, directory, outputDir)
    }
  }

  def extract(tarFile: File, outputDir: File): Unit = {
    if (useNativeTar) {
      sbt.Process("tar", Seq("pxf", tarFile.getAbsolutePath, "-C", outputDir.getAbsolutePath)).!!
    } else {
      Unpack(tarFile, outputDir)
    }
  }

  def useNativeTar: Boolean = {
    !SystemSettings.hasNio
  }
}

object Pack {
  def apply(archiveBasename: String, workingDir: File, directory: String, outputDir: File): File = {
    val archive = outputDir / (archiveBasename + ".tar")
    val tarOutput = new FileOutputStream(archive)

    def recursiveListFiles(f: File): Array[File] = {
      val these = f.listFiles
      these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    }

    def addFilesToTar(tarBall: ArchiveOutputStream, dir: File): Unit = recursiveListFiles(dir).foreach {
      file =>
        if (!file.isDirectory) {
          val tarFile = new TarArchiveEntry(file, sbt.IO.relativize(workingDir, file).get)
          tarFile.setSize(file.length())
          if (java.nio.file.Files.isExecutable(java.nio.file.FileSystems.getDefault.getPath(file.getAbsolutePath))) {
            tarFile.setMode(493)
          }
          tarBall.putArchiveEntry(tarFile)
          IOUtils.copy(new FileInputStream(file), tarBall)
          tarBall.closeArchiveEntry()
        }
    }

    val tarBall = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.TAR, tarOutput)
    tarBall.asInstanceOf[TarArchiveOutputStream].setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
    try {
      addFilesToTar(tarBall, workingDir / directory)
    } finally {
      tarBall.close()
    }

    val outputFile = outputDir / (archiveBasename + ".tgz")
    sbt.IO.gzip(archive, outputFile)
    sbt.IO.delete(archive)
    outputFile
  }
}

object Unpack {

  def apply(tarFile: File, outputDir: File): Unit = {

    def decompress(input: BufferedInputStream): InputStream =
      Try(new CompressorStreamFactory().createCompressorInputStream(input)) match {
        case Success(i) => new BufferedInputStream(i)
        case Failure(_) => input
      }

    def extract(input: InputStream): ArchiveInputStream =
      new ArchiveStreamFactory().createArchiveInputStream(input)

    val input = extract(decompress(new BufferedInputStream(new FileInputStream(tarFile))))
    def stream: Stream[ArchiveEntry] = input.getNextEntry match {
      case null => Stream.empty
      case entry => entry #:: stream
    }

    for (entry <- stream) {
      if (entry.isDirectory) {
        sbt.IO.createDirectory(outputDir / entry.getName)
      } else {
        val destPath = outputDir / entry.getName
        destPath.createNewFile

        val btoRead = Array.ofDim[Byte](1024)
        val bout = new BufferedOutputStream(new FileOutputStream(destPath))
        try {
          var len = input.read(btoRead)
          while (len != -1) {
            bout.write(btoRead, 0, len)
            len = input.read(btoRead)
          }
        } finally {
          bout.close()
        }

        val mode = entry.asInstanceOf[TarArchiveEntry].getMode
        if (mode == 493) {
          destPath.setExecutable(true)
        }
      }
    }

  }

}
