/*
 * Copyright (C) 2016 Alexander Christian <alex(at)root1.de>. All rights reserved.
 * 
 * This file is part of KnxProjParser.
 *
 *   KnxProjParser is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   KnxProjParser is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with KnxProjParser.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.root1.knxprojparser;

/**
 *
 * @author achristian
 */
public class FileNotSupportedException extends Exception {

    public FileNotSupportedException() {
    }

    public FileNotSupportedException(String message) {
        super(message);
    }

    public FileNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileNotSupportedException(Throwable cause) {
        super(cause);
    }

}
