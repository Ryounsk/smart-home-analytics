import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { SystemConfig } from '../../types';

interface SystemState {
  config: SystemConfig;
  loading: boolean;
  error: string | null;
  theme: 'light' | 'dark';
  language: 'zh-CN' | 'en-US';
}

const initialState: SystemState = {
  config: {
    refreshInterval: 30,
    errorThreshold: 5,
    warningThreshold: 2,
    dataRetentionDays: 30,
    notificationEnabled: true
  },
  loading: false,
  error: null,
  theme: 'light',
  language: 'zh-CN'
};

// 异步action - 保存系统配置
export const saveSystemConfig = createAsyncThunk(
  'system/saveSystemConfig',
  async (config: SystemConfig, { rejectWithValue }) => {
    try {
      // 这里应该调用API保存配置
      // 暂时模拟保存
      await new Promise(resolve => setTimeout(resolve, 1000));
      return config;
    } catch (error: any) {
      return rejectWithValue(error.message || '保存配置失败');
    }
  }
);

const systemSlice = createSlice({
  name: 'system',
  initialState,
  reducers: {
    toggleTheme: (state) => {
      state.theme = state.theme === 'light' ? 'dark' : 'light';
    },
    setLanguage: (state, action: PayloadAction<'zh-CN' | 'en-US'>) => {
      state.language = action.payload;
    },
    clearError: (state) => {
      state.error = null;
    }
  },
  extraReducers: (builder) => {
    builder
      // 处理saveSystemConfig
      .addCase(saveSystemConfig.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(saveSystemConfig.fulfilled, (state, action: PayloadAction<SystemConfig>) => {
        state.loading = false;
        state.config = action.payload;
      })
      .addCase(saveSystemConfig.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  }
});

export const { toggleTheme, setLanguage, clearError } = systemSlice.actions;
export default systemSlice.reducer;