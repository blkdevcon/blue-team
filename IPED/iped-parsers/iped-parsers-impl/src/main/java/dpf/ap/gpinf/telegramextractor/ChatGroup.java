/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.util.HashSet;
import java.util.Set;

public class ChatGroup extends Chat {

    private Set<Long> members = new HashSet<>();

    public ChatGroup(long id, Contact c, String name) {
        super(id, c, name);
        this.setGroup(true);
    }

    public Set<Long> getMembers() {
        return members;
    }

    public void addMember(long id) {
        if (!members.contains(id)) {
            members.add(id);
        }
    }

}
