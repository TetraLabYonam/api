import { create } from 'zustand';

export const useRoomStore = create((set) => ({
  rooms: [],
  currentRoom: null,
  roomState: null,

  setRooms: (rooms) => set({ rooms }),

  setCurrentRoom: (room) => set({ currentRoom: room }),

  setRoomState: (state) => set({ roomState: state }),

  updateRoomState: (roomUid, state) =>
    set((prev) => ({
      rooms: prev.rooms.map((r) =>
        r.roomUid === roomUid ? { ...r, ...state } : r
      ),
    })),

  clearCurrentRoom: () => set({ currentRoom: null, roomState: null }),
}));
