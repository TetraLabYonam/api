import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useUserStore = create(
  persist(
    (set) => ({
      userKey: null,
      currentTicket: null,

      setUserKey: (key) => set({ userKey: key }),

      setCurrentTicket: (ticket) => set({ currentTicket: ticket }),

      clearUser: () => set({ userKey: null, currentTicket: null }),
    }),
    {
      name: 'user-storage',
    }
  )
);
