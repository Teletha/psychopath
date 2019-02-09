/*
 * Copyright (C) 2019 psychopath Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package psychopath;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.LinkPermission;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;

import kiss.I;
import kiss.Observer;
import kiss.Signal;
import kiss.WiseRunnable;

public abstract class Location<Self extends Location> implements Comparable<Location>, PathOperatable {

    /** The separator flag. */
    private static final boolean useNativeSeparator = java.io.File.separatorChar == '/';

    /** The actual location. */
    protected final Path path;

    /**
     * @param path
     */
    protected Location(Path path) {
        this.path = path == null ? Path.of("") : path;
    }

    /**
     * Returns the name of the file or directory denoted by this path as a {@code Location} object.
     * The file name is the <em>farthest</em> element from the root in the directory hierarchy.
     *
     * @return A path representing the name of the file or directory, or {@code null} if this path
     *         has zero elements
     */
    public final String name() {
        return String.valueOf(path.getFileName());
    }

    /**
     * Retrieve the base name of this {@link Location}.
     * 
     * @return A base name.
     */
    public final String base() {
        String name = name();
        int index = name.lastIndexOf(".");
        return index == -1 ? name : name.substring(0, index);
    }

    /**
     * Locate {@link Location} with the specified new base name, but extension is same.
     * 
     * @param newBaseName A new base name.
     * @return New located {@link Location}.
     */
    public final Self base(String newBaseName) {
        String extension = extension();
        return convert(path.resolveSibling(extension.isEmpty() ? newBaseName : newBaseName + "." + extension));
    }

    /**
     * Retrieve the extension of this {@link Location}.
     * 
     * @return An extension or empty if it has no extension.
     */
    public final String extension() {
        String name = name();
        int index = name.lastIndexOf(".");
        return index == -1 ? "" : name.substring(index + 1);
    }

    /**
     * Locate {@link Location} with the specified new extension, but base name is same.
     * 
     * @param newExtension A new extension.
     * @return New located {@link Location}.
     */
    public final Self extension(String newExtension) {
        return convert(path.resolveSibling(base() + "." + newExtension));
    }

    /**
     * Returns the path expression of this {@link Location}.
     * 
     * @return A path to this {@link Location}.
     */
    public final String path() {
        if (useNativeSeparator) {
            return path.toString();
        } else {
            return path.toString().replace(java.io.File.separatorChar, '/');
        }
    }

    /**
     * Returns a {@code Location} object representing the absolute path of this path.
     * <p>
     * If this path is already {@link Path#isAbsolute absolute} then this method simply returns this
     * path. Otherwise, this method resolves the path in an implementation dependent manner,
     * typically by resolving the path against a file system default directory. Depending on the
     * implementation, this method may throw an I/O error if the file system is not accessible.
     *
     * @return A {@code Location} object representing the absolute path.
     * @throws java.io.IOError if an I/O error occurs
     * @throws SecurityException In the case of the default provider, a security manager is
     *             installed, and this path is not absolute, then the security manager's
     *             {@link SecurityManager#checkPropertyAccess(String) checkPropertyAccess} method is
     *             invoked to check access to the system property {@code user.dir}
     */
    public final Self absolutize() {
        if (path.isAbsolute()) {
            return (Self) this;
        } else {
            return convert(path.toAbsolutePath());
        }
    }

    /**
     * Constructs a relative path between this path and a given path.
     * <p>
     * Relativization is the inverse of {@link #resolve(Path) resolution}. This method attempts to
     * construct a {@link #isAbsolute relative} path that when {@link #resolve(Path) resolved}
     * against this path, yields a path that locates the same file as the given path. For example,
     * on UNIX, if this path is {@code "/a/b"} and the given path is {@code "/a/b/c/d"} then the
     * resulting relative path would be {@code "c/d"}. Where this path and the given path do not
     * have a {@link #getRoot root} component, then a relative path can be constructed. A relative
     * path cannot be constructed if only one of the paths have a root component. Where both paths
     * have a root component then it is implementation dependent if a relative path can be
     * constructed. If this path and the given path are {@link #equals equal} then an <i>empty
     * path</i> is returned.
     * <p>
     * For any two {@link #normalize normalized} paths <i>p</i> and <i>q</i>, where <i>q</i> does
     * not have a root component, <blockquote> <i>p</i>{@code .relativize(}<i>p</i>
     * {@code .resolve(}<i>q</i>{@code )).equals(}<i>q</i>{@code )} </blockquote>
     * <p>
     * When symbolic links are supported, then whether the resulting path, when resolved against
     * this path, yields a path that can be used to locate the {@link Files#isSameFile same} file as
     * {@code other} is implementation dependent. For example, if this path is {@code "/a/b"} and
     * the given path is {@code "/a/x"} then the resulting relative path may be {@code
     * "../x"}. If {@code "b"} is a symbolic link then is implementation dependent if
     * {@code "a/b/../x"} would locate the same file as {@code "/a/x"}.
     *
     * @param other the path to relativize against this path
     * @return the resulting relative path, or an empty path if both paths are equal
     * @throws IllegalArgumentException if {@code other} is not a {@code Path} that can be
     *             relativized against this path
     */
    public final <T extends Location> T relativize(T other) {
        return (T) other.convert(path.relativize(other.path));
    }

    /**
     * Returns the <em>parent path</em>, or {@code null} if this path does not have a parent.
     * <p>
     * The parent of this path object consists of this path's root component, if any, and each
     * element in the path except for the <em>farthest</em> from the root in the directory
     * hierarchy. This method does not access the file system; the path or its parent may not exist.
     * Furthermore, this method does not eliminate special names such as "." and ".." that may be
     * used in some implementations. On UNIX for example, the parent of "{@code /a/b/c}" is
     * "{@code /a/b}", and the parent of {@code "x/y/.}" is "{@code x/y}". This method may be used
     * with the {@link #normalize normalize} method, to eliminate redundant names, for cases where
     * <em>shell-like</em> navigation is required.
     * <p>
     * If this path has more than one element, and no root component, then this method is equivalent
     * to evaluating the expression: <blockquote><pre>
     * subpath(0,&nbsp;getNameCount()-1);
     * </pre></blockquote>
     *
     * @return A {@link Location} representing the path's parent
     */
    public final Directory parent() {
        return Locator.directory(path.getParent());
    }

    /**
     * Return a {@link Signal} to iterate over all entries in this {@link Location}. The entry
     * returned by the directory stream's {@link DirectoryStream#iterator iterator} are of type
     * {@code
     * Path}, each one representing an entry in the directory. The {@code Path} objects are obtained
     * as if by {@link Path#resolve(Path) resolving} the name of the directory entry against
     * {@code dir}.
     * <p>
     * When an implementation supports operations on entries in the directory that execute in a
     * race-free manner then the returned directory stream is a {@link SecureDirectoryStream}.
     *
     * @return A {@link Signal} which iterate over all entries in this {@link Location}.
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, the {@link SecurityManager#checkRead(String) checkRead} method is
     *             invoked to check read access to the directory.
     */
    public abstract Signal<Location<?>> children();

    /**
     * Return a {@link Signal} to iterate over all entries in this {@link Location}. The entry
     * returned by the directory stream's {@link DirectoryStream#iterator iterator} are of type
     * {@code
     * Path}, each one representing an entry in the directory. The {@code Path} objects are obtained
     * as if by {@link Path#resolve(Path) resolving} the name of the directory entry against
     * {@code dir}.
     * <p>
     * When an implementation supports operations on entries in the directory that execute in a
     * race-free manner then the returned directory stream is a {@link SecureDirectoryStream}.
     *
     * @return A {@link Signal} which iterate over all entries in this {@link Location}.
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, the {@link SecurityManager#checkRead(String) checkRead} method is
     *             invoked to check read access to the directory.
     */
    public abstract Signal<Location<?>> descendant();

    /**
     * Reads a file's attributes as a bulk operation.
     * <p>
     * The {@code type} parameter is the type of the attributes required and this method returns an
     * instance of that type if supported. All implementations support a basic set of file
     * attributes and so invoking this method with a {@code type} parameter of {@code
     * BasicFileAttributes.class} will not throw {@code
     * UnsupportedOperationException}.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     * <p>
     * It is implementation specific if all file attributes are read as an atomic operation with
     * respect to other file system operations.
     * <p>
     * <b>Usage Example:</b> Suppose we want to read a file's attributes in bulk: <pre>
     *    Path path = ...
     *    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
     * </pre> Alternatively, suppose we want to read file's POSIX attributes without following
     * symbolic links: <pre>
     *    PosixFileAttributes attrs =
     *        Files.readAttributes(path, PosixFileAttributes.class, NOFOLLOW_LINKS);
     * </pre>
     *
     * @param path the path to the file
     * @return the file attributes
     * @throws UnsupportedOperationException if an attributes of the given type are not supported
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method is
     *             invoked to check read access to the file. If this method is invoked to read
     *             security sensitive attributes then the security manager may be invoke to check
     *             for additional permissions.
     */
    public final BasicFileAttributes attr() {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw I.quiet(e);
        }
    }

    /**
     * Returns the size of a file (in bytes). The size may differ from the actual size on the file
     * system due to compression, support for sparse files, or other reasons. The size of files that
     * are not {@link #isRegularFile regular} files is implementation specific and therefore
     * unspecified.
     *
     * @return the file size, in bytes
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     * @see BasicFileAttributes#size
     */
    public final long size() {
        try {
            return Files.size(path);
        } catch (NoSuchFileException e) {
            return 0;
        } catch (IOException e) {
            throw I.quiet(e);
        }
    }

    /**
     * Returns a file's last modified time.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     *
     * @param options options indicating how symbolic links are handled
     * @return a {@code FileTime} representing the time the file was last modified, or an
     *         implementation specific default when a time stamp to indicate the time of last
     *         modification is not supported by the file system
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     * @see BasicFileAttributes#lastModifiedTime
     */
    public final long lastModified(LinkOption... options) {
        if (Files.notExists(path)) {
            return -1;
        }

        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw I.quiet(e);
        }
    }

    /**
     * Returns a file's last modified time.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     *
     * @param options options indicating how symbolic links are handled
     * @return a {@code FileTime} representing the time the file was last modified, or an
     *         implementation specific default when a time stamp to indicate the time of last
     *         modification is not supported by the file system
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     * @see BasicFileAttributes#lastModifiedTime
     */
    public final Instant lastModifiedTime(LinkOption... options) {
        return Instant.ofEpochMilli(lastModified(options));
    }

    /**
     * Returns a file's last modified time.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     *
     * @param options options indicating how symbolic links are handled
     * @return a {@code FileTime} representing the time the file was last modified, or an
     *         implementation specific default when a time stamp to indicate the time of last
     *         modification is not supported by the file system
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     * @see BasicFileAttributes#lastModifiedTime
     */
    public final ZonedDateTime lastModifiedDateTime(LinkOption... options) {
        return ZonedDateTime.from(lastModifiedTime(options));
    }

    /**
     * Updates a file's last modified time attribute. The file time is converted to the epoch and
     * precision supported by the file system. Converting from finer to coarser granularities result
     * in precision loss. The behavior of this method when attempting to set the last modified time
     * when it is not supported by the file system or is outside the range supported by the
     * underlying file store is not defined. It may or not fail by throwing an {@code IOException}.
     *
     * @param time the new last modified time
     * @return Chainable API
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkWrite(String) checkWrite} method
     *             denies write access to the file.
     */
    public final Self lastModified(FileTime time) {
        if (time != null) {
            try {
                Files.setLastModifiedTime(path, time);
            } catch (IOException e) {
                throw I.quiet(e);
            }
        }
        return (Self) this;
    }

    /**
     * Updates a file's last modified time attribute. The file time is converted to the epoch and
     * precision supported by the file system. Converting from finer to coarser granularities result
     * in precision loss. The behavior of this method when attempting to set the last modified time
     * when it is not supported by the file system or is outside the range supported by the
     * underlying file store is not defined. It may or not fail by throwing an {@code IOException}.
     *
     * @param time the new last modified time
     * @return Chainable API
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkWrite(String) checkWrite} method
     *             denies write access to the file.
     */
    public final Self lastModified(ChronoLocalDateTime time) {
        if (time != null) {
            lastModified(time.atZone(ZoneId.systemDefault()));
        }
        return (Self) this;
    }

    /**
     * Updates a file's last modified time attribute. The file time is converted to the epoch and
     * precision supported by the file system. Converting from finer to coarser granularities result
     * in precision loss. The behavior of this method when attempting to set the last modified time
     * when it is not supported by the file system or is outside the range supported by the
     * underlying file store is not defined. It may or not fail by throwing an {@code IOException}.
     *
     * @param time the new last modified time
     * @return Chainable API
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkWrite(String) checkWrite} method
     *             denies write access to the file.
     */
    public final Self lastModified(ChronoZonedDateTime time) {
        if (time != null) {
            lastModified(time.toInstant());
        }
        return (Self) this;
    }

    /**
     * Updates a file's last modified time attribute. The file time is converted to the epoch and
     * precision supported by the file system. Converting from finer to coarser granularities result
     * in precision loss. The behavior of this method when attempting to set the last modified time
     * when it is not supported by the file system or is outside the range supported by the
     * underlying file store is not defined. It may or not fail by throwing an {@code IOException}.
     *
     * @param time the new last modified time
     * @return Chainable API
     * @throws IOException if an I/O error occurs
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkWrite(String) checkWrite} method
     *             denies write access to the file.
     */
    public final Self lastModified(Instant time) {
        if (time != null) {
            lastModified(FileTime.from(time));
        }
        return (Self) this;
    }

    /**
     * Retrieve the {@link BasicFileAttributes} of this {@link Location}.
     * 
     * @return A {@link BasicFileAttributes}.
     */
    public final BasicFileAttributes attribute(LinkOption... options) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, options);
        } catch (IOException e) {
            throw I.quiet(e);
        }
    }

    /**
     * Tells whether or not this path is absolute.
     * <p>
     * An absolute path is complete in that it doesn't need to be combined with other path
     * information in order to locate a file.
     *
     * @return {@code true} if, and only if, this path is absolute
     */
    public final boolean isAbsolute() {
        return path.isAbsolute();
    }

    /**
     * Tells whether or not this path is relative.
     * <p>
     * An absolute path is complete in that it doesn't need to be combined with other path
     * information in order to locate a file.
     *
     * @return {@code true} if, and only if, this path is relative
     */
    public final boolean isRelative() {
        return !path.isAbsolute();
    }

    /**
     * Tests whether this location does not exist or not. This method is intended for cases where it
     * is required to take action when it can be confirmed that a file does not exist.
     * 
     * @param options options indicating how symbolic links are handled
     * @return {@code true} if the file does not exist; {@code false} if the file exists or its
     *         existence cannot be determined
     */
    public final boolean isAbsent(LinkOption... options) {
        return Files.notExists(path, options);
    }

    /**
     * Tests whether this location does exist or not. This method is intended for cases where it is
     * required to take action when it can be confirmed that a file does exist.
     * 
     * @param options options indicating how symbolic links are handled
     * @return {@code false} if the file does not exist; {@code true} if the file exists or its
     *         existence cannot be determined
     */
    public final boolean isPresent(LinkOption... options) {
        return Files.exists(path, options);
    }

    /**
     * Tests whether a file is readable. This method checks that a file exists and that this Java
     * virtual machine has appropriate privileges that would allow it open the file for reading.
     * Depending on the implementation, this method may require to read file permissions, access
     * control lists, or other file attributes in order to check the effective access to the file.
     * Consequently, this method may not be atomic with respect to other file system operations.
     * <p>
     * Note that the result of this method is immediately outdated, there is no guarantee that a
     * subsequent attempt to open the file for reading will succeed (or even that it will access the
     * same file). Care should be taken when using this method in security sensitive applications.
     *
     * @return {@code true} if the file exists and is readable; {@code false} if the file does not
     *         exist, read access would be denied because the Java virtual machine has insufficient
     *         privileges, or access cannot be determined
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, the {@link SecurityManager#checkRead(String) checkRead} is invoked to
     *             check read access to the file.
     */
    public final boolean isReadable() {
        return Files.isReadable(path);
    }

    /**
     * Tests whether a file is a regular file with opaque content.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     * <p>
     * Where it is required to distinguish an I/O exception from the case that the file is not a
     * regular file then the file attributes can be read with the
     * {@link #readAttributes(Path,Class,LinkOption[]) readAttributes} method and the file type
     * tested with the {@link BasicFileAttributes#isRegularFile} method.
     *
     * @param options options indicating how symbolic links are handled
     * @return {@code true} if the file is a regular file; {@code false} if the file does not exist,
     *         is not a regular file, or it cannot be determined if the file is a regular file or
     *         not.
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     */
    public final boolean isFile(LinkOption... options) {
        return Files.isRegularFile(path, options);
    }

    /**
     * Tests whether a file is a directory.
     * <p>
     * The {@code options} array may be used to indicate how symbolic links are handled for the case
     * that the file is a symbolic link. By default, symbolic links are followed and the file
     * attribute of the final target of the link is read. If the option
     * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then symbolic links are not
     * followed.
     * <p>
     * Where it is required to distinguish an I/O exception from the case that the file is not a
     * directory then the file attributes can be read with the
     * {@link #readAttributes(Path,Class,LinkOption[]) readAttributes} method and the file type
     * tested with the {@link BasicFileAttributes#isDirectory} method.
     *
     * @param options options indicating how symbolic links are handled
     * @return {@code true} if the file is a directory; {@code false} if the file does not exist, is
     *         not a directory, or it cannot be determined if the file is a directory or not.
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     */
    public final boolean isDirectory(LinkOption... options) {
        return Files.isDirectory(path, options);
    }

    /**
     * Tests whether a file is a symbolic link.
     * <p>
     * Where it is required to distinguish an I/O exception from the case that the file is not a
     * symbolic link then the file attributes can be read with the
     * {@link #readAttributes(Path,Class,LinkOption[]) readAttributes} method and the file type
     * tested with the {@link BasicFileAttributes#isSymbolicLink} method.
     *
     * @return {@code true} if the file is a symbolic link; {@code false} if the file does not
     *         exist, is not a symbolic link, or it cannot be determined if the file is a symbolic
     *         link or not.
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, its {@link SecurityManager#checkRead(String) checkRead} method denies
     *             read access to the file.
     */
    public final boolean isSymbolicLink() {
        return Files.isSymbolicLink(path);
    }

    /**
     * Test whether this location indicates root or not.
     * 
     * @return
     */
    public final boolean isRoot() {
        return path.getNameCount() == 0;
    }

    /**
     * Convert to {@link Directory} if it is possible. If this {@link Location} indicate
     * {@link File}, throw error.
     * 
     * @throws IllegalStateException If this {@link Location} is NOT {@link Directory}.
     * @return {@link Directory} interface.
     */
    public final Directory asDirectory() {
        if (isAbsent() || isDirectory()) {
            return Locator.directory(path);
        } else {
            throw new IllegalStateException("[" + path + "] is not directory.");
        }
    }

    /**
     * Convert to {@link File} if it is possible. If this {@link Location} indicate
     * {@link Directory}, throw error.
     * 
     * @throws IllegalStateException If this {@link Location} is NOT {@link File}.
     * @return {@link File} interface.
     */
    public final File asFile() {
        if (isAbsent() || !isDirectory()) {
            return Locator.file(path);
        } else {
            throw new IllegalStateException("[" + path + "] is not file.");
        }
    }

    /**
     * Returns a {@link File} object representing this path. Where this {@code
     * Path} is associated with the default provider, then this method is equivalent to returning a
     * {@code File} object constructed with the {@code String} representation of this path.
     * <p>
     * If this path was created by invoking the {@code File} {@link File#toPath toPath} method then
     * there is no guarantee that the {@code
     * File} object returned by this method is {@link #equals equal} to the original {@code File}.
     *
     * @implSpec The default implementation is equivalent for this path to: <pre>{@code
     *     new File(toString());
     * }</pre> if the {@code FileSystem} which created this {@code Path} is the default file
     *           system; otherwise an {@code UnsupportedOperationException} is thrown.
     * @return a {@code File} object representing this path
     * @throws UnsupportedOperationException if this {@code Path} is not associated with the default
     *             provider
     */
    public final java.io.File asJavaFile() {
        return path.toFile();
    }

    /**
     * Returns a {@link Path java.nio.file.Path} object constructed from the this abstract path. The
     * resulting {@code Path} is associated with the {@link java.nio.file.FileSystems#getDefault
     * default-filesystem}.
     * <p>
     * The first invocation of this method works as if invoking it were equivalent to evaluating the
     * expression: <blockquote><pre>
     * {@link java.nio.file.FileSystems#getDefault FileSystems.getDefault}().{@link
     * java.nio.file.FileSystem#getPath getPath}(this.{@link #getPath getPath}());
     * </pre></blockquote> Subsequent invocations of this method return the same {@code Path}.
     * <p>
     * If this abstract pathname is the empty abstract pathname then this method returns a
     * {@code Path} that may be used to access the current user directory.
     *
     * @return a {@code Path} constructed from this abstract path
     * @throws java.nio.file.InvalidPathException if a {@code Path} object cannot be constructed
     *             from the abstract path (see {@link java.nio.file.FileSystem#getPath
     *             FileSystem.getPath})
     */
    public final Path asJavaPath() {
        return path;
    }

    public abstract Self create();

    /**
     * Shorthand method for <code>moveTo(parent().parent())</code>.
     */
    public final void moveUp() {
        moveTo(parent().parent());
    }

    /**
     * <p>
     * Observe the file system change and raises events when a file, directory, or file in a
     * directory, changes.
     * </p>
     * <p>
     * You can watch for changes in files and subdirectories of the specified directory.
     * </p>
     * <p>
     * The operating system interpret a cut-and-paste action or a move action as a rename action for
     * a directory and its contents. If you cut and paste a folder with files into a directory being
     * watched, the {@link Observer} object reports only the directory as new, but not its contents
     * because they are essentially only renamed.
     * </p>
     * <p>
     * Common file system operations might raise more than one event. For example, when a file is
     * moved from one directory to another, several Modify and some Create and Delete events might
     * be raised. Moving a file is a complex operation that consists of multiple simple operations,
     * therefore raising multiple events. Likewise, some applications might cause additional file
     * system events that are detected by the {@link Observer}.
     * </p>
     *
     * @return A observable event stream.
     * @throws NullPointerException If the specified path or listener is <code>null</code>.
     * @throws SecurityException In the case of the default provider, and a security manager is
     *             installed, the {@link SecurityManager#checkRead(String)} method is invoked to
     *             check read access to the source file, the
     *             {@link SecurityManager#checkWrite(String)} is invoked to check write access to
     *             the target file. If a symbolic link is copied the security manager is invoked to
     *             check {@link LinkPermission}("symbolic").
     */
    public abstract Signal<WatchEvent<Location>> observe();

    /**
     * Implements the same behaviour as the "touch" utility on Unix. It creates a new file with size
     * 0 or, if the file exists already, it is opened and closed without modifying it, but updating
     * the file date and time.
     */
    public final Self touch() {
        if (isAbsent()) {
            create();
        } else {
            lastModified(ZonedDateTime.now());
        }
        return (Self) this;
    }

    /**
     * Try to acquire exclusive lock for this {@link Location}.
     * 
     * @return
     */
    public abstract FileLock lock(WiseRunnable failed);

    /**
     * Test matching the specified pattern to this {@link Location}.
     * 
     * @param pattern A glob pattern.
     * @return A result.
     */
    public final boolean match(String pattern) {
        boolean result = true;

        if (pattern.charAt(0) == '!') {
            pattern = pattern.substring(1);
            result = false;
        }
        return path.getFileSystem().getPathMatcher("glob:".concat(pattern)).matches(path) == result;
    }

    /**
     * Test matching the specified pattern to this {@link Location}.
     * 
     * @param pattern A glob pattern.
     * @return A result.
     */
    public final boolean match(String... patterns) {
        return match(List.of(patterns));
    }

    /**
     * Test matching the specified pattern to this {@link Location}.
     * 
     * @param pattern A glob pattern.
     * @return A result.
     */
    public final boolean match(List<String> patterns) {
        for (String pattern : patterns) {
            if (match(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return path.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object other) {
        if (other instanceof Location) {
            return path.equals(((Location) other).path);
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
        return path.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int compareTo(Location o) {
        return path.compareTo(o.path);
    }

    /**
     * Convert from {@link Path}.
     * 
     * @param path A target path.
     * @return The {@link Location}.
     */
    protected abstract Self convert(Path path);
}
