import { configureStore } from '@reduxjs/toolkit';
import realtimeReducer from './slices/realtimeSlice';
import offlineReducer from './slices/offlineSlice';
import systemReducer from './slices/systemSlice';

const store = configureStore({
  reducer: {
    realtime: realtimeReducer,
    offline: offlineReducer,
    system: systemReducer,
  },
  middleware: (getDefaultMiddleware) => 
    getDefaultMiddleware({
      serializableCheck: false
    }),
  devTools: import.meta.env.VITE_APP_ENV !== 'production'
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export default store;